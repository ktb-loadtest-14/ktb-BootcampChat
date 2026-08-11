package com.ktb.chatapp.service;

import com.ktb.chatapp.storage.PresignedUpload;

public record ProfileImageUploadPreparation(
        boolean directUpload,
        String objectKey,
        String imageUrl,
        PresignedUpload presignedUpload) {

    public static ProfileImageUploadPreparation proxyUpload() {
        return new ProfileImageUploadPreparation(false, null, null, null);
    }
}
