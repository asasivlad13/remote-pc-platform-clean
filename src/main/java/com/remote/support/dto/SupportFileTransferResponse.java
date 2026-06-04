package com.remote.support.dto;

import java.time.LocalDateTime;

public record SupportFileTransferResponse(
        Long id,
        Long supportSessionId,
        Long senderId,
        String senderUsername,
        Long recipientId,
        String recipientUsername,
        String originalFilename,
        String contentType,
        Long sizeBytes,
        String status,
        LocalDateTime createdAt,
        LocalDateTime decidedAt
) {
}