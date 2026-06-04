package com.remote.education.dto;

import java.time.LocalDateTime;

public record EducationFileResponse(
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