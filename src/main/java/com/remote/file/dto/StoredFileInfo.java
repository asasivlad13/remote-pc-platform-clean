package com.remote.file.dto;

import java.time.LocalDateTime;

public class StoredFileInfo {

    private String fileId;
    private String fileName;
    private long fileSize;
    private String downloadUrl;
    private String encryptionKey;
    private String iv;
    private boolean downloaded;
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