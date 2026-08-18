package com.remote.frame.controller;

import com.remote.core.service.CurrentUserService;
import com.remote.frame.dto.FrameUploadRequest;
import com.remote.frame.service.FrameService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/frames")
public class FrameController {

    private final FrameService frameService;
    private final CurrentUserService currentUserService;

    @PostMapping("/upload")
    public ResponseEntity<Void> uploadFrame(
            @Valid @RequestBody FrameUploadRequest request
    ) {
        frameService.uploadFrame(request);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/latest/{pcId}")
    public ResponseEntity<Map<String, String>> getLatestFrame(
            @PathVariable Long pcId
    ) {
        return ResponseEntity.ok(
                frameService.getLatestFrame(pcId)
        );
    }

    @GetMapping("/my-pcs-frames")
    public ResponseEntity<Map<Long, String>> getMyPcsFrames(
            HttpServletRequest request
    ) {
        String email =
                currentUserService.extractUsername(request);

        return ResponseEntity.ok(
                frameService.getMyPcsFrames(email)
        );
    }
}