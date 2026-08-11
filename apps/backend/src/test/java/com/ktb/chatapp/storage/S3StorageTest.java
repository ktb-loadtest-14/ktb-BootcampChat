package com.ktb.chatapp.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3StorageTest {

    @Mock private S3Client s3Client;

    private S3Storage storage;

    @BeforeEach
    void setUp() {
        storage = new S3Storage(s3Client, "test-bucket");
    }

    @Test
    void put_uploadsObjectAndReturnsStorageKey() {
        byte[] content = "image-data".getBytes(StandardCharsets.UTF_8);

        StoredObject stored = storage.put(
                new ByteArrayInputStream(content), "chat/image.jpg", "image/jpeg", content.length);

        assertEquals("chat/image.jpg", stored.key());
        assertEquals(content.length, stored.size());
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void open_returnsS3ObjectAsResource() throws Exception {
        byte[] content = "image-data".getBytes(StandardCharsets.UTF_8);
        ResponseInputStream<GetObjectResponse> response = new ResponseInputStream<>(
                GetObjectResponse.builder().contentLength((long) content.length).build(),
                new ByteArrayInputStream(content));
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(response);

        var resource = storage.open("chat/image.jpg");

        assertTrue(resource.isPresent());
        assertArrayEquals(content, resource.orElseThrow().getInputStream().readAllBytes());
    }

    @Test
    void delete_deletesS3Object() {
        storage.delete("chat/image.jpg");

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }
}
