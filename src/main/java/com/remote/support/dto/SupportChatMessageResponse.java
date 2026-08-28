package com.remote.support.dto;

import java.time.Instant;

public record SupportChatMessageResponse(
        Long id,
        Long senderId,
        String senderUsername,
        String message,
        Instant createdAt
) {
}