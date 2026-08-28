package com.remote.support.dto;

import java.time.Instant;

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
        Instant createdAt,
        Instant decidedAt
) {
}