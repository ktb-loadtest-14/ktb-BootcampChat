package com.ktb.chatapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb.chatapp.model.File;
import com.ktb.chatapp.repository.FileRepository;
import com.ktb.chatapp.storage.PresignedUpload;
import com.ktb.chatapp.storage.StoragePort;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileService 직접 업로드")
class LocalFileServiceDirectUploadTest {

    @Mock private StoragePort storagePort;
    @Mock private FileRepository fileRepository;

    private LocalFileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new LocalFileService(storagePort, fileRepository);
    }

    @Test
    @DisplayName("S3 준비 요청은 pending 메타데이터와 Presigned URL을 반환한다")
    void prepareUpload_s3_persistsPendingMetadata() {
        PresignedUpload presigned = new PresignedUpload(
                URI.create("https://bucket.s3.amazonaws.com/chat/file.png?sig=test"),
                Map.of("Content-Type", "image/png"),
                Instant.now().plusSeconds(300));
        when(storagePort.presignPut(any(), eq("image/png"), eq(10L)))
                .thenReturn(Optional.of(presigned));
        when(fileRepository.save(any(File.class))).thenAnswer(invocation -> {
            File file = invocation.getArgument(0);
            file.setId("file-1");
            return file;
        });

        FileUploadPreparation result = fileService.prepareUpload(
                "여행.png", "image/png", 10L, "user-1");

        assertThat(result.directUpload()).isTrue();
        assertThat(result.file().getId()).isEqualTo("file-1");
        assertThat(result.file().getPath()).startsWith("chat/");
        assertThat(result.file().getUploadCompleted()).isFalse();
        assertThat(result.file().isUploadReady()).isFalse();
        assertThat(result.file().getUploadExpiresAt()).isNotNull();
        assertThat(result.presignedUpload()).isSameAs(presigned);
        verify(storagePort, never()).put(any(), any(), any(), eq(10L));
    }

    @Test
    @DisplayName("PUT 성공 확정 후에만 파일이 채팅 첨부 가능 상태가 된다")
    void completeUpload_marksPendingFileReady() {
        File pending = File.builder()
                .id("file-1")
                .user("user-1")
                .uploadCompleted(false)
                .uploadExpiresAt(Instant.now().plusSeconds(300))
                .build();
        when(fileRepository.findById("file-1")).thenReturn(Optional.of(pending));
        when(fileRepository.save(pending)).thenReturn(pending);

        FileUploadResult result = fileService.completeUpload("file-1", "user-1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFile().isUploadReady()).isTrue();
        assertThat(result.getFile().getUploadExpiresAt()).isNull();
        verify(fileRepository).save(pending);
    }
}
