package com.remote.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupportChatMessageRequest(
        @NotBlank
        @Size(max = 2000)
        String message
) {
}