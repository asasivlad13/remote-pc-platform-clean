package com.remote.file.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remote.file.dto.StoredFileInfo;
import com.remote.history.service.ConnectionLogActivityService;
import com.remote.pc.model.Pc;
import com.remote.pc.repository.PcRepository;
import com.remote.websocket.agent.AgentWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class FileTransferService {

    private final FileStorageService fileStorageService;
    private final AgentWebSocketHandler agentWebSocketHandler;
    private final ConnectionLogActivityService connectionLogActivityService;
    private final PcRepository pcRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public StoredFileInfo uploadFile(Long pcId,
                                     MultipartFile file,
                                     String baseUrl) throws Exception {
        Pc pc = pcRepository.findById(pcId)
                .orElseThrow(() -> new IllegalArgumentException("ПК не найден"));

        if (pc.getUser() == null) {
            throw new IllegalArgumentException("У ПК не найден владелец");
        }

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

        try {
            connectionLogActivityService.incrementFilesSent(
                    pc.getUser().getUsername(),
                    pc.getName()
            );
        } catch (Exception e) {
            log.warn(
                    "Failed to update files sent counter, but file command was already sent: pcId={}, fileId={}",
                    pcId,
                    storedFileInfo.getFileId(),
                    e
            );
        }

        log.info(
                "Encrypted file uploaded and download command sent: pcId={}, fileName={}, fileId={}, fileSize={}",
                pcId,
                storedFileInfo.getFileName(),
                storedFileInfo.getFileId(),
                storedFileInfo.getFileSize()
        );

        return storedFileInfo;
    }

    public ResponseEntity<?> downloadFile(String fileId) {
        try {
            StoredFileInfo info = fileStorageService.getInfo(fileId);

            if (info == null) {
                log.warn("File download requested but file info was not found: fileId={}", fileId);
                return ResponseEntity.notFound().build();
            }

            Resource resource = fileStorageService.loadEncryptedAsResourceOnce(fileId);

            String encodedFileName = URLEncoder
                    .encode(info.getFileName() + ".enc", StandardCharsets.UTF_8)
                    .replace("+", "%20");

            log.info(
                    "Encrypted file download started: fileId={}, fileName={}",
                    fileId,
                    info.getFileName()
            );

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFileName
                    )
                    .body(resource);

        } catch (IllegalStateException e) {
            log.warn("One-time download link already used: fileId={}", fileId);

            return ResponseEntity
                    .status(HttpStatus.GONE)
                    .body("Download link already used");

        } catch (Exception e) {
            log.error("Encrypted file download failed: fileId={}", fileId, e);

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Download error: " + e.getMessage());
        }
    }
}