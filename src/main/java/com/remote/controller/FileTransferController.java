package com.remote.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remote.dto.StoredFileInfo;
import com.remote.handler.AgentWebSocketHandler;
import com.remote.service.FileStorageService;
import com.remote.service.ServerUrlService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileTransferController {

    private final FileStorageService fileStorageService;
    private final AgentWebSocketHandler agentWebSocketHandler;
    private final ServerUrlService serverUrlService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FileTransferController(FileStorageService fileStorageService,
                                  AgentWebSocketHandler agentWebSocketHandler,
                                  ServerUrlService serverUrlService) {
        this.fileStorageService = fileStorageService;
        this.agentWebSocketHandler = agentWebSocketHandler;
        this.serverUrlService = serverUrlService;
    }

    @PostMapping("/upload")
    public StoredFileInfo uploadFile(@RequestParam("pcId") Long pcId,
                                     @RequestParam("file") MultipartFile file,
                                     HttpServletRequest request) throws Exception {
        String baseUrl = serverUrlService.getBaseUrl(request);

        StoredFileInfo storedFileInfo = fileStorageService.storeEncrypted(file, baseUrl);

        Map<String, Object> command = new HashMap<>();
        command.put("type", "command");
        command.put("pcId", pcId);
        command.put("action", "FILE_DOWNLOAD");
        command.put("fileId", storedFileInfo.getFileId());
        command.put("fileName", storedFileInfo.getFileName());
        command.put("fileSize", storedFileInfo.getFileSize());
        command.put("downloadUrl", storedFileInfo.getDownloadUrl());
        command.put("encryptionKey", storedFileInfo.getEncryptionKey());
        command.put("iv", storedFileInfo.getIv());

        agentWebSocketHandler.sendCommandToAgent(
                pcId,
                objectMapper.valueToTree(command)
        );

        System.out.println("🔐 File uploaded, encrypted and one-time download command sent:");
        System.out.println("  PC ID: " + pcId);
        System.out.println("  File: " + storedFileInfo.getFileName());
        System.out.println("  URL: " + storedFileInfo.getDownloadUrl());

        return storedFileInfo;
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<?> downloadFile(@PathVariable String fileId) {
        try {
            StoredFileInfo info = fileStorageService.getInfo(fileId);

            if (info == null) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = fileStorageService.loadEncryptedAsResourceOnce(fileId);

            String encodedFileName = URLEncoder.encode(info.getFileName() + ".enc", StandardCharsets.UTF_8)
                    .replace("+", "%20");

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFileName
                    )
                    .body(resource);

        } catch (IllegalStateException e) {
            return ResponseEntity
                    .status(HttpStatus.GONE)
                    .body("Download link already used");

        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Download error: " + e.getMessage());
        }
    }
}