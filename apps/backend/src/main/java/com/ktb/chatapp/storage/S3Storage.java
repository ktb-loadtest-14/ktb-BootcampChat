package com.ktb.chatapp.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

@Component
@ConditionalOnProperty(name = "file.storage.type", havingValue = "s3")
public class S3Storage implements StoragePort {

    private final S3Presigner s3Presigner;
    private final String bucket;
    private final String cdnDomain;
    private final Duration uploadUrlTtl;

    public S3Storage(
            S3Presigner s3Presigner,
            @Value("${file.storage.s3.bucket}") String bucket,
            @Value("${file.storage.cdn-domain}") String cdnDomain,
            @Value("${file.storage.s3.presign-ttl:PT5M}") Duration uploadUrlTtl) {
        Assert.hasText(bucket, "AWS_S3_BUCKET must not be empty when S3 storage is enabled");
        Assert.hasText(cdnDomain, "S3_CDN_DOMAIN must not be empty when S3 storage is enabled");
        Assert.isTrue(!uploadUrlTtl.isZero() && !uploadUrlTtl.isNegative(),
                "S3 presigned URL TTL must be positive");
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.cdnDomain = normalizeDomain(cdnDomain);
        this.uploadUrlTtl = uploadUrlTtl;
    }

    @Override
    public StoredObject put(InputStream content, String key, String contentType, long size) {
        throw new UnsupportedOperationException(
                "S3 파일은 presigned URL을 사용해 브라우저에서 직접 업로드해야 합니다.");
    }

    @Override
    public Optional<Resource> open(String key) {
        throw new UnsupportedOperationException(
                "S3 객체 조회는 애플리케이션 IAM이 아니라 CloudFront를 통해 수행해야 합니다.");
    }

    @Override
    public void delete(String key) {
        throw new UnsupportedOperationException(
                "현재 S3 IAM 정책에서는 객체 삭제를 지원하지 않습니다.");
    }

    @Override
    public Optional<PresignedUpload> presignPut(String key, String contentType, long size) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(size)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(uploadUrlTtl)
                .putObjectRequest(putObjectRequest)
                .build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

        return Optional.of(new PresignedUpload(
                URI.create(presigned.url().toString()),
                Map.of("Content-Type", contentType),
                Instant.now().plus(uploadUrlTtl)));
    }

    @Override
    public boolean requiresDirectUpload() {
        return true;
    }

    @Override
    public Optional<URI> offloadUrl(String key, Duration ttl, ContentDisposition disposition) {
        return Optional.of(URI.create(cdnDomain + "/" + key));
    }

    private static String normalizeDomain(String domain) {
        String trimmed = domain.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
