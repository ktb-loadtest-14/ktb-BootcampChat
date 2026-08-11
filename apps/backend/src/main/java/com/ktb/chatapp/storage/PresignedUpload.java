package com.ktb.chatapp.storage;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/** 브라우저가 스토리지에 직접 PUT할 때 필요한 일회성 업로드 정보. */
public record PresignedUpload(
        URI url,
        Map<String, String> requiredHeaders,
        Instant expiresAt) {
}
