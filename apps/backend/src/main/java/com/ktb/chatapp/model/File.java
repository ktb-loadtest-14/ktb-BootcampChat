package com.ktb.chatapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "files")
public class File {

    @Id
    private String id;

    private String filename;

    private String originalname;

    private String mimetype;

    private long size;

    private String path;

    @Field("user")
    private String user;

    @Field("uploadDate")
    @CreatedDate
    private LocalDateTime uploadDate;

    /** false인 동안에는 채팅 메시지에 첨부할 수 없다. null은 마이그레이션 전 기존 파일로 간주한다. */
    private Boolean uploadCompleted;

    /** 완료되지 않은 업로드 메타데이터를 MongoDB TTL 인덱스로 자동 정리한다. */
    @Indexed(name = "pending_upload_expiry", expireAfter = "0s")
    private Instant uploadExpiresAt;

    public boolean isUploadReady() {
        return uploadCompleted == null || uploadCompleted;
    }

    /**
     * 미리보기 지원 여부 확인
     */
    public boolean isPreviewable() {
        List<String> previewableTypes = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "video/mp4", "video/webm",
            "audio/mpeg", "audio/wav",
            "application/pdf"
        );
        return previewableTypes.contains(this.mimetype);
    }

    /**
     * Content-Disposition 헤더 생성
     */
    public String getContentDisposition(String type) {
        String encodedFilename = URLEncoder.encode(this.originalname, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        
        return String.format(
            "%s; filename=\"%s\"; filename*=UTF-8''%s",
            type,
            this.originalname,
            encodedFilename
        );
    }

    /**
     * 파일 URL 생성
     */
    public String getFileUrl(String type) {
        return String.format("/api/files/%s/%s",
            type,
            URLEncoder.encode(this.filename, StandardCharsets.UTF_8));
    }
}
