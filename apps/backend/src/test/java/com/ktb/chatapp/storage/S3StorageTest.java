package com.ktb.chatapp.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ContentDisposition;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class S3StorageTest {

    @Mock private S3Presigner s3Presigner;
    @Mock private PresignedPutObjectRequest presignedPutObjectRequest;

    private S3Storage storage;

    @BeforeEach
    void setUp() {
        storage = new S3Storage(
                s3Presigner,
                "test-bucket",
                "https://cdn.example.test/",
                Duration.ofMinutes(5));
    }

    @Test
    void presignPut_returnsBrowserUploadContractWithoutCallingS3() throws Exception {
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presignedPutObjectRequest);
        when(presignedPutObjectRequest.url())
                .thenReturn(new URL("https://test-bucket.s3.ap-northeast-2.amazonaws.com/chat/image.jpg?sig=test"));

        PresignedUpload upload = storage.presignPut("chat/image.jpg", "image/jpeg", 10L)
                .orElseThrow();

        assertThat(upload.url().toString()).contains("chat/image.jpg?sig=test");
        assertThat(upload.requiredHeaders()).containsEntry("Content-Type", "image/jpeg");
        verify(s3Presigner).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    void put_rejectsBackendByteProxying() {
        byte[] content = "image-data".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> storage.put(
                new ByteArrayInputStream(content), "chat/image.jpg", "image/jpeg", content.length))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("presigned URL");
    }

    @Test
    void open_rejectsApplicationGetObject() {
        assertThatThrownBy(() -> storage.open("chat/image.jpg"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("CloudFront");
    }

    @Test
    void offloadUrl_returnsCloudFrontUrl() {
        var url = storage.offloadUrl(
                "chat/image.jpg", Duration.ofMinutes(5), ContentDisposition.inline().build()).orElseThrow();

        assertThat(url.toString()).isEqualTo("https://cdn.example.test/chat/image.jpg");
    }

    @Test
    void delete_doesNotCallS3OutsideConfiguredIamPermissions() {
        assertThatThrownBy(() -> storage.delete("chat/image.jpg"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("IAM");
    }
}
