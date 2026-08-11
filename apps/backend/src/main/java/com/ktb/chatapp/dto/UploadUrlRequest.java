package com.ktb.chatapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record UploadUrlRequest(
        @NotBlank String filename,
        @NotBlank String contentType,
        @Positive long size) {
}
