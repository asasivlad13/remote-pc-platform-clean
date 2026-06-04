package com.remote.education.dto;

public record EducationAgentResponse(
        Boolean hasAgent,
        Long pcId,
        String pcName,
        String status,
        Boolean canShareScreen,
        String webrtcUrl,
        String streamName,
        Integer screenWidth,
        Integer screenHeight
) {
}