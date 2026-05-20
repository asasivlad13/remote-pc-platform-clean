package com.remote.controller;

import com.remote.config.JwtUtil;
import com.remote.model.EducationFileTransfer;
import com.remote.service.EducationFileTransferService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/education/files")
public class EducationFileTransferController {

    private final EducationFileTransferService fileService;
    private final JwtUtil jwtUtil;

    public EducationFileTransferController(EducationFileTransferService fileService,
                                           JwtUtil jwtUtil) {
        this.fileService = fileService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/{sessionCode}")
    public FileResponse upload(@PathVariable String sessionCode,
                               @RequestParam(value = "recipientId", required = false) Long recipientId,
                               @RequestParam("file") MultipartFile file,
                               HttpServletRequest request) {
        String username = extractUsername(request);

        return toResponse(fileService.upload(username, sessionCode, recipientId, file));
    }

    @GetMapping("/{sessionCode}")
    public List<FileResponse> getFiles(@PathVariable String sessionCode,
                                       HttpServletRequest request) {
        String username = extractUsername(request);

        return fileService.getVisibleFiles(username, sessionCode)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable Long fileId,
                                             HttpServletRequest request) {
        String username = extractUsername(request);

        EducationFileTransfer file = fileService.getFileForDownload(username, fileId);
        Resource resource = fileService.loadResource(file);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.getOriginalFilename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        file.getContentType() != null ? file.getContentType() : "application/octet-stream"
                ))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    private FileResponse toResponse(EducationFileTransfer file) {
        return new FileResponse(
                file.getId(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getSender() != null ? file.getSender().getId() : null,
                file.getSender() != null ? file.getSender().getUsername() : null,
                file.getRecipient() != null ? file.getRecipient().getId() : null,
                file.getRecipient() != null ? file.getRecipient().getUsername() : null,
                file.getStatus().name(),
                file.getCreatedAt()
        );
    }

    private String extractUsername(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization header is missing");
        }

        String token = authHeader.substring(7);

        if (!jwtUtil.validateToken(token)) {
            throw new IllegalArgumentException("Invalid JWT token");
        }

        return jwtUtil.extractUsername(token);
    }

    public record FileResponse(
            Long id,
            String originalFilename,
            String contentType,
            Long sizeBytes,
            Long senderId,
            String senderUsername,
            Long recipientId,
            String recipientUsername,
            String status,
            LocalDateTime createdAt
    ) {
    }
}