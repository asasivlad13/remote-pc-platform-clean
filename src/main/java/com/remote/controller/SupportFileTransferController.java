package com.remote.controller;

import com.remote.service.SupportFileTransferService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/support/sessions/{sessionCode}/files")
public class SupportFileTransferController {

    private final SupportFileTransferService supportFileTransferService;

    public SupportFileTransferController(SupportFileTransferService supportFileTransferService) {
        this.supportFileTransferService = supportFileTransferService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getFiles(@PathVariable String sessionCode) {
        return ResponseEntity.ok(supportFileTransferService.getFiles(getCurrentUsername(), sessionCode));
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(@PathVariable String sessionCode,
                                                      @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(supportFileTransferService.uploadFromOperator(getCurrentUsername(), sessionCode, file));
    }

    @PostMapping("/{fileId}/accept")
    public ResponseEntity<Map<String, Object>> accept(@PathVariable String sessionCode,
                                                      @PathVariable Long fileId) {
        return ResponseEntity.ok(supportFileTransferService.accept(getCurrentUsername(), sessionCode, fileId));
    }

    @PostMapping("/{fileId}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable String sessionCode,
                                                      @PathVariable Long fileId) {
        return ResponseEntity.ok(supportFileTransferService.reject(getCurrentUsername(), sessionCode, fileId));
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<byte[]> download(@PathVariable String sessionCode,
                                           @PathVariable Long fileId) {
        return supportFileTransferService.download(getCurrentUsername(), sessionCode, fileId);
    }

    private String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
