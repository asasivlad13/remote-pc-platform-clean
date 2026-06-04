package com.remote.support.controller;

import com.remote.core.service.CurrentUserService;
import com.remote.support.dto.SupportFileTransferResponse;
import com.remote.support.service.SupportFileTransferService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/support/sessions/{sessionCode}/files")
public class SupportFileTransferController {

    private final SupportFileTransferService supportFileTransferService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public List<SupportFileTransferResponse> getFiles(@PathVariable String sessionCode,
                                                      HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return supportFileTransferService.getFiles(username, sessionCode);
    }

    @PostMapping("/upload")
    public SupportFileTransferResponse upload(@PathVariable String sessionCode,
                                              @RequestParam("file") MultipartFile file,
                                              HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return supportFileTransferService.uploadFromOperator(username, sessionCode, file);
    }

    @PostMapping("/{fileId}/accept")
    public SupportFileTransferResponse accept(@PathVariable String sessionCode,
                                              @PathVariable Long fileId,
                                              HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return supportFileTransferService.accept(username, sessionCode, fileId);
    }

    @PostMapping("/{fileId}/reject")
    public SupportFileTransferResponse reject(@PathVariable String sessionCode,
                                              @PathVariable Long fileId,
                                              HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return supportFileTransferService.reject(username, sessionCode, fileId);
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<byte[]> download(@PathVariable String sessionCode,
                                           @PathVariable Long fileId,
                                           HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);
        return supportFileTransferService.download(username, sessionCode, fileId);
    }
}