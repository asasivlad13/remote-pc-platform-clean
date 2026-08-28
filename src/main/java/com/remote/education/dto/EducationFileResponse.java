package com.remote.education.dto;

import java.time.Instant;

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
        Instant createdAt
) {
}