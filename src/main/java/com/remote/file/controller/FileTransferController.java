package com.remote.file.controller;

import com.remote.file.dto.StoredFileInfo;
import com.remote.file.service.FileTransferService;
import com.remote.file.service.ServerUrlService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/files")
public class FileTransferController {

    private final FileTransferService fileTransferService;
    private final ServerUrlService serverUrlService;

    @PostMapping("/upload")
    public StoredFileInfo uploadFile(@RequestParam("pcId") Long pcId,
                                     @RequestParam("file") MultipartFile file,
                                     HttpServletRequest request) throws Exception {
        String baseUrl = serverUrlService.getBaseUrl(request);
        return fileTransferService.uploadFile(pcId, file, baseUrl);
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<?> downloadFile(@PathVariable String fileId) {
        return fileTransferService.downloadFile(fileId);
    }
}