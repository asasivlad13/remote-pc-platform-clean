package com.remote.support.dto;

import com.remote.support.model.SupportSessionStatus;

import java.time.Instant;

public record SupportSessionResponse(
        Long id,
        String sessionCode,
        String title,
        SupportSessionStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,

        Long operatorId,
        String operatorUsername,

        Long clientId,
        String clientUsername,

        Long clientPcId,
        String clientPcName,
        String clientPcStatus,
        String clientPcWebrtcUrl,
        String clientPcStreamName,
        Integer clientPcScreenWidth,
        Integer clientPcScreenHeight,
        Integer screenWidth,
        Integer screenHeight,

        Boolean controlRequested,
        Boolean controlAllowed,
        Instant controlRequestedAt,
        Instant controlAllowedAt
) {
}