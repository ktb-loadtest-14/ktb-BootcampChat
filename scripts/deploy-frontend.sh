#!/usr/bin/env bash

set -Eeuo pipefail

log() {
  printf '[frontend-deploy] %s\n' "$*"
}

fail() {
  printf '[frontend-deploy] ERROR: %s\n' "$*" >&2
  exit 1
}

for command_name in docker ssh; do
  command -v "$command_name" >/dev/null 2>&1 || fail "필수 명령을 찾을 수 없습니다: $command_name"
done

: "${DEPLOY_HOSTS:?GitHub Variable FRONTEND_HOSTS가 필요합니다.}"
: "${IMAGE_NAME:?IMAGE_NAME이 필요합니다.}"
: "${IMAGE_TAG:?image_tag 입력값이 필요합니다.}"
: "${GHCR_USERNAME:?GHCR_USERNAME이 필요합니다.}"
: "${GHCR_TOKEN:?packages:read 권한의 GITHUB_TOKEN이 필요합니다.}"

DEPLOY_USER="${DEPLOY_USER:-ubuntu}"
SSH_KEY_PATH="${SSH_KEY_PATH:-${HOME}/.ssh/deploy_ed25519}"
FRONTEND_ENV_FILE="${FRONTEND_ENV_FILE:-/opt/bootcamp-chat/frontend.env}"
EXPECTED_HOST_COUNT="${EXPECTED_HOST_COUNT:-2}"

[[ "$DEPLOY_USER" =~ ^[a-z_][a-z0-9_-]*$ ]] || fail "올바르지 않은 DEPLOY_USER입니다."
[[ "$GHCR_USERNAME" =~ ^[A-Za-z0-9-]+$ ]] || fail "올바르지 않은 GHCR_USERNAME입니다."
[[ -r "$SSH_KEY_PATH" ]] || fail "SSH 개인키를 읽을 수 없습니다: $SSH_KEY_PATH"
[[ "$IMAGE_TAG" == "test" || "$IMAGE_TAG" =~ ^sha-[0-9a-f]{7,40}$ ]] || \
  fail "image_tag는 test 또는 sha-<커밋 SHA> 형식이어야 합니다."

# 공백 또는 줄바꿈으로 등록된 GitHub Variable을 호스트 배열로 변환한다.
hosts=()
while IFS= read -r host; do
  [[ -n "$host" ]] && hosts+=("$host")
done < <(printf '%s\n' "$DEPLOY_HOSTS" | tr '[:space:]' '\n')

[[ "${#hosts[@]}" -eq "$EXPECTED_HOST_COUNT" ]] || \
  fail "FRONTEND_HOSTS는 ${EXPECTED_HOST_COUNT}대여야 합니다. 현재: ${#hosts[@]}대"

declare -A seen_hosts=()
for host in "${hosts[@]}"; do
  [[ "$host" =~ ^[A-Za-z0-9.-]+$ ]] || fail "올바르지 않은 호스트입니다: $host"
  [[ -z "${seen_hosts[$host]:-}" ]] || fail "중복된 호스트입니다: $host"
  seen_hosts[$host]=1
done

# test처럼 변경 가능한 태그도 여기서 한 번만 pull하고 digest 참조로 고정한다.
source_image="${IMAGE_NAME}:${IMAGE_TAG}"
log "이미지 확인: $source_image"
docker pull "$source_image"

resolved_image=""
while IFS= read -r candidate; do
  if [[ "$candidate" == "${IMAGE_NAME}@sha256:"* ]]; then
    resolved_image="$candidate"
    break
  fi
done < <(docker image inspect "$source_image" --format '{{range .RepoDigests}}{{println .}}{{end}}')

[[ -n "$resolved_image" ]] || fail "이미지 digest를 확인할 수 없습니다: $source_image"
log "고정된 배포 이미지: $resolved_image"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  printf 'resolved_image=%s\n' "$resolved_image" >> "$GITHUB_OUTPUT"
fi

ssh_options=(
  -i "$SSH_KEY_PATH"
  -o BatchMode=yes
  -o ConnectTimeout=10
  -o StrictHostKeyChecking=accept-new
)

for host in "${hosts[@]}"; do
  target="${DEPLOY_USER}@${host}"
  log "배포 시작: $target"

  # 토큰을 명령행 인자로 넘기지 않고 표준입력으로 전달한다.
  if ! printf '%s' "$GHCR_TOKEN" | ssh "${ssh_options[@]}" "$target" \
    docker login ghcr.io --username "$GHCR_USERNAME" --password-stdin >/dev/null; then
    fail "GHCR 로그인 실패: $target"
  fi

  printf -v remote_command 'bash -s -- %q %q' "$resolved_image" "$FRONTEND_ENV_FILE"

  if ! ssh "${ssh_options[@]}" "$target" "$remote_command" <<'REMOTE_SCRIPT'
set -Eeuo pipefail

image_ref="$1"
env_file="$2"
container_name="ktb-chat-frontend"

command -v docker >/dev/null 2>&1 || {
  echo "Docker를 찾을 수 없습니다." >&2
  exit 1
}

http_ok() {
  if command -v curl >/dev/null 2>&1; then
    curl --fail --silent --show-error --max-time 3 "http://127.0.0.1:3000/" >/dev/null
  elif command -v wget >/dev/null 2>&1; then
    wget --quiet --spider --timeout=3 "http://127.0.0.1:3000/"
  else
    echo "health check에 curl 또는 wget이 필요합니다." >&2
    return 1
  fi
}

run_frontend() {
  local target_image="$1"
  local run_args=(
    docker run --detach
    --name "$container_name"
    --restart unless-stopped
    --publish 3000:3000
  )

  if [[ -r "$env_file" ]]; then
    run_args+=(--env-file "$env_file")
  else
    echo "주의: $env_file 파일이 없어 Dockerfile 기본 환경변수로 실행합니다."
  fi

  run_args+=("$target_image")
  "${run_args[@]}"
}

previous_image_id="$(docker inspect --format '{{.Image}}' "$container_name" 2>/dev/null || true)"

docker pull "$image_ref"
docker rm --force "$container_name" >/dev/null 2>&1 || true

if ! run_frontend "$image_ref" >/dev/null; then
  echo "새 프론트 컨테이너 실행에 실패했습니다." >&2
  if [[ -n "$previous_image_id" ]]; then
    echo "직전 이미지로 복구합니다: $previous_image_id"
    run_frontend "$previous_image_id" >/dev/null || true
  fi
  exit 1
fi

healthy=false
for _ in {1..30}; do
  if http_ok; then
    healthy=true
    break
  fi

  if [[ "$(docker inspect --format '{{.State.Running}}' "$container_name" 2>/dev/null || true)" != "true" ]]; then
    break
  fi
  sleep 2
done

if [[ "$healthy" != "true" ]]; then
  echo "프론트 health check가 실패했습니다." >&2
  docker logs --tail 100 "$container_name" >&2 || true
  docker rm --force "$container_name" >/dev/null 2>&1 || true

  if [[ -n "$previous_image_id" ]]; then
    echo "직전 이미지로 복구합니다: $previous_image_id"
    run_frontend "$previous_image_id" >/dev/null || true
  fi
  exit 1
fi

echo "프론트 배포 및 health check 성공"
REMOTE_SCRIPT
  then
    ssh "${ssh_options[@]}" "$target" docker logout ghcr.io >/dev/null 2>&1 || true
    fail "배포 실패: $target"
  fi

  ssh "${ssh_options[@]}" "$target" docker logout ghcr.io >/dev/null 2>&1 || true
  log "배포 완료: $target"
done

log "모든 프론트 서버가 동일한 digest로 배포되었습니다: $resolved_image"
