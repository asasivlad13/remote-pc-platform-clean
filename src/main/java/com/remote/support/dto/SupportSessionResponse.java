package com.remote.support.dto;

import com.remote.support.model.SupportSessionStatus;

import java.time.LocalDateTime;

public record SupportSessionResponse(
        Long id,
        String sessionCode,
        String title,
        SupportSessionStatus status,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,

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
        LocalDateTime controlRequestedAt,
        LocalDateTime controlAllowedAt
) {
}