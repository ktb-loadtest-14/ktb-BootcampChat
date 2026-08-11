package com.ktb.chatapp.dto;

import jakarta.validation.constraints.NotBlank;

public record CompleteProfileImageUploadRequest(@NotBlank String objectKey) {
}
