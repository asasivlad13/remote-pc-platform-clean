package com.remote.file.service;

import com.remote.file.dto.StoredFileInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileStorageService {

    private final Path storageDir;
    private final FileCryptoService fileCryptoService;
    private final Map<String, StoredFileInfo> files = new ConcurrentHashMap<>();

    public FileStorageService(
            @Value("${remote.files.storage-dir:uploads/remote-files}") String storageDir,
            FileCryptoService fileCryptoService
    ) throws Exception {
        this.storageDir = Path.of(storageDir).toAbsolutePath().normalize();
        this.fileCryptoService = fileCryptoService;

        Files.createDirectories(this.storageDir);
    }

    public StoredFileInfo storeEncrypted(MultipartFile file, String publicBaseUrl) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String fileId = UUID.randomUUID().toString();
        String originalFileName = sanitizeFileName(file.getOriginalFilename());
        String storedFileName = fileId + "_" + originalFileName + ".enc";

        Path targetPath = storageDir.resolve(storedFileName).normalize();

        FileCryptoService.CryptoData cryptoData = fileCryptoService.generateCryptoData();

        try (var inputStream = file.getInputStream();
             var outputStream = Files.newOutputStream(targetPath)) {
            fileCryptoService.encrypt(
                    inputStream,
                    outputStream,
                    cryptoData.encryptionKey(),
                    cryptoData.iv()
            );
        }

        String downloadUrl = publicBaseUrl + "/api/files/download/" + fileId;

        StoredFileInfo info = new StoredFileInfo(
                fileId,
                originalFileName,
                file.getSize(),
                downloadUrl,
                cryptoData.encryptionKey(),
                cryptoData.iv()
        );

        files.put(fileId, info);

        System.out.println("🔐 File encrypted and stored:");
        System.out.println("  Original: " + originalFileName);
        System.out.println("  Stored: " + targetPath);

        return info;
    }

    public synchronized Resource loadEncryptedAsResourceOnce(String fileId) throws Exception {
        StoredFileInfo info = files.get(fileId);

        if (info == null) {
            throw new IllegalArgumentException("File not found by id: " + fileId);
        }

        if (info.isDownloaded()) {
            throw new IllegalStateException("Download link already used: " + fileId);
        }

        Path filePath = storageDir.resolve(fileId + "_" + info.getFileName() + ".enc").normalize();

        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("Encrypted file does not exist on disk");
        }

        info.markDownloaded();

        try {
            return new UrlResource(filePath.toUri());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Cannot load encrypted file", e);
        }
    }

    public StoredFileInfo getInfo(String fileId) {
        return files.get(fileId);
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "unknown_file";
        }

        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}