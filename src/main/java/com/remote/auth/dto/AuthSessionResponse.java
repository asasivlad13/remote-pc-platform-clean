package com.remote.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record AuthSessionResponse(
        String token,
        UUID sessionId,
        Instant refreshExpiresAt
) {
}