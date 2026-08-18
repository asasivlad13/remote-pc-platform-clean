package com.remote.pc.dto;

import com.remote.pc.model.PcStatus;

import java.time.Instant;

public record PcResponseDto(
        Long id,
        String name,
        String macAddress,
        PcStatus status,
        Instant lastSeenAt
) {
}