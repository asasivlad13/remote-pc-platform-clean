package com.remote.pc.dto;

import com.remote.pc.model.PcStatus;

import java.time.Instant;

public record PcDetailsResponse(
        Long id,
        String name,
        String macAddress,
        PcStatus status,
        Instant lastSeenAt,
        Integer screenWidth,
        Integer screenHeight,
        String webrtcUrl,
        String streamName
) {
}