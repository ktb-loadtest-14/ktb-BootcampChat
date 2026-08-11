package com.ktb.chatapp.service;

import com.ktb.chatapp.storage.StorageKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 스토리지 key를 응답용 URL로 조립한다. DB에는 key만 저장하고, URL은 응답 경계에서만 만든다.
 *
 * <p>조립은 <b>한 방향</b>이다. URL을 key로 되돌리는 파싱을 두면 옛 URL 형식을 받아들이는 하위호환 분기가
 * 자라난다 — 저장 형식이 둘로 갈라지는 시작점이다.
 *
 * <p>S3 저장 모드이고 CDN 도메인이 설정되어 있으면 CloudFront 절대 URL을 반환한다. 그 외에는 채팅 파일을
 * 실제 백엔드 미리보기 API로, 프로필 이미지를 공개 프로필 파일 API로 연결한다.
 */
@Component
public class FileUrl {

    private static final String API_PREFIX = "/api/files/";

    private final boolean cdnEnabled;
    private final String cdnDomain;

    public FileUrl(
            @Value("${file.storage.type:local}") String storageType,
            @Value("${file.storage.cdn-domain:}") String cdnDomain) {
        this.cdnEnabled = "s3".equalsIgnoreCase(storageType) && StringUtils.hasText(cdnDomain);
        this.cdnDomain = normalizeDomain(cdnDomain);
    }

    /** 값이 없으면(null·빈 문자열) 그대로 통과시킨다 — 프로필 이미지 미설정 상태를 URL로 만들지 않는다. */
    public String of(String key) {
        if (key == null || key.isEmpty()) {
            return key;
        }

        if (key.startsWith("http://") || key.startsWith("https://")) {
            return key;
        }

        String normalizedKey = key.startsWith("/") ? key.substring(1) : key;
        if (cdnEnabled) {
            return cdnDomain + "/" + normalizedKey;
        }

        if (StorageKey.isChat(normalizedKey)) {
            return API_PREFIX + "view/" + StorageKey.nameOf(normalizedKey);
        }

        return API_PREFIX + normalizedKey;
    }

    private static String normalizeDomain(String domain) {
        if (!StringUtils.hasText(domain)) {
            return "";
        }
        return domain.endsWith("/") ? domain.substring(0, domain.length() - 1) : domain;
    }
}
