package com.remote.support.dto;

import java.time.LocalDateTime;

public record SupportChatMessageResponse(
        Long id,
        Long senderId,
        String senderUsername,
        String message,
        LocalDateTime createdAt
) {
}