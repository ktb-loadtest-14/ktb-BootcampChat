package com.ktb.chatapp.storage;

import java.io.InputStream;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@ConditionalOnProperty(name = "file.storage.type", havingValue = "s3")
public class S3Storage implements StoragePort {

    private final S3Client s3Client;
    private final String bucket;

    public S3Storage(S3Client s3Client, @Value("${file.storage.s3.bucket}") String bucket) {
        Assert.hasText(bucket, "AWS_S3_BUCKET must not be empty when S3 storage is enabled");
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Override
    public StoredObject put(InputStream content, String key, String contentType, long size) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(size)
                .build();

        s3Client.putObject(request, RequestBody.fromInputStream(content, size));
        return new StoredObject(key, size);
    }

    @Override
    public Optional<Resource> open(String key) {
        try {
            ResponseInputStream<GetObjectResponse> object = s3Client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            return Optional.of(new InputStreamResource(object));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
