package com.ktb.chatapp.service;

import com.ktb.chatapp.model.File;
import com.ktb.chatapp.storage.PresignedUpload;

public record FileUploadPreparation(
        boolean directUpload,
        File file,
        PresignedUpload presignedUpload) {

    public static FileUploadPreparation proxyUpload() {
        return new FileUploadPreparation(false, null, null);
    }

    public static FileUploadPreparation direct(File file, PresignedUpload presignedUpload) {
        return new FileUploadPreparation(true, file, presignedUpload);
    }
}
