package com.remote.auth.dto;

import java.time.Instant;

public record DeviceCredentialResponse(
        String credential,
        int version,
        Instant issuedAt
) {
}