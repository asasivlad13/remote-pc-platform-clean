package com.remote.file.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class StoredFileInfo {

    @NotBlank
    private String fileId;

    @NotBlank
    @Size(max = 255)
    private String fileName;

    @Min(0)
    private long fileSize;

    @NotBlank
    @Size(max = 1000)
    private String downloadUrl;

    @NotBlank
    private String encryptionKey;

    @NotBlank
    private String iv;

    private boolean downloaded;

    @NotNull
    private LocalDateTime createdAt;

    private LocalDateTime downloadedAt;

    public StoredFileInfo(String fileId,
                          String fileName,
                          long fileSize,
                          String downloadUrl,
                          String encryptionKey,
                          String iv) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.downloadUrl = downloadUrl;
        this.encryptionKey = encryptionKey;
        this.iv = iv;
        this.downloaded = false;
        this.createdAt = LocalDateTime.now();
    }

    public String getFileId() {
        return fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public String getIv() {
        return iv;
    }

    public boolean isDownloaded() {
        return downloaded;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getDownloadedAt() {
        return downloadedAt;
    }

    public void markDownloaded() {
        this.downloaded = true;
        this.downloadedAt = LocalDateTime.now();
    }
}