package com.remote.pc.dto;

import com.remote.pc.model.PcStatus;

import java.time.LocalDateTime;

public record PcResponseDto(
        Long id,
        String name,
        String macAddress,
        PcStatus status,
        LocalDateTime lastConnection
) {
}