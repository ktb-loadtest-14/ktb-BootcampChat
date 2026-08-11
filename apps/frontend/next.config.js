const path = require('path');

const workspaceRoot = path.join(__dirname, '../..');
const additionalDevOrigins = (process.env.DEV_ALLOWED_ORIGINS || '')
  .split(',')
  .map((origin) => origin.trim())
  .filter(Boolean);

const publicDocumentCacheControl = 'public, max-age=0, s-maxage=60, stale-while-revalidate=30';

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // 같은 LAN의 다른 기기에서 dev 서버(/_next 자산·HMR)에 접근하도록 허용한다. dev 전용이고
  // localhost는 Next가 항상 허용하므로 이 목록이 로컬 접속에 영향을 주지 않는다.
  // 패턴은 점 단위로 매칭돼서 사설망 IP는 네 칸을 다 써야 한다 — '192.168.*'는 매칭되지 않는다.
  allowedDevOrigins: [
    '192.168.*.*',
    '10.*.*.*',
    ...additionalDevOrigins
  ],
  transpilePackages: ['@vapor-ui/core', '@vapor-ui/icons'],
  turbopack: {
    root: workspaceRoot
  },
  // Docker 빌드를 위한 standalone 출력 모드 (개발 환경에는 영향 없음)
  output: 'standalone',
  // monorepo에서 standalone 빌드 시 중첩 경로 방지
  outputFileTracingRoot: workspaceRoot,
  // 로그인과 회원가입 HTML은 빌드 시 생성되는 비개인화 정적 문서다. 브라우저에는 저장하지
  // 않고 공유 프록시에서만 짧게 재사용해 동시 접속 시 Next 프로세스의 문서 응답 tail을 줄인다.
  async headers() {
    return [
      {
        source: '/',
        headers: [{ key: 'Cache-Control', value: publicDocumentCacheControl }],
      },
      {
        source: '/register',
        headers: [{ key: 'Cache-Control', value: publicDocumentCacheControl }],
      },
    ];
  },
};

module.exports = nextConfig;
