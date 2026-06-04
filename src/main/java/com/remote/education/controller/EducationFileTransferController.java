package com.remote.education.controller;

import com.remote.core.service.CurrentUserService;
import com.remote.education.dto.EducationFileResponse;
import com.remote.education.service.EducationFileTransferService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/education/files")
public class EducationFileTransferController {

    private final EducationFileTransferService fileService;
    private final CurrentUserService currentUserService;

    @PostMapping("/{sessionCode}")
    public EducationFileResponse upload(@PathVariable String sessionCode,
                                        @RequestParam(value = "recipientId", required = false) Long recipientId,
                                        @RequestParam("file") MultipartFile file,
                                        HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);

        return fileService.uploadAndReturnResponse(username, sessionCode, recipientId, file);
    }

    @GetMapping("/{sessionCode}")
    public List<EducationFileResponse> getFiles(@PathVariable String sessionCode,
                                                HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);

        return fileService.getVisibleFileResponses(username, sessionCode);
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable Long fileId,
                                             HttpServletRequest request) {
        String username = currentUserService.extractUsername(request);

        return fileService.downloadResponse(username, fileId);
    }
}