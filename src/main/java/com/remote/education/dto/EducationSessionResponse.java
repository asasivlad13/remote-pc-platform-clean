package com.remote.education.dto;

import java.time.Instant;

public record EducationSessionResponse(
        Long id,
        String sessionCode,
        String title,
        String status,
        String teacherUsername,
        String teacherDisplayName,
        Long teacherPcId,
        String teacherPcName,
        String teacherPcWebrtcUrl,
        String teacherPcStreamName,
        Integer teacherPcScreenWidth,
        Integer teacherPcScreenHeight,
        Integer maxStudents,
        Boolean allowStudentControl,
        Boolean allowFileTransfer,
        Boolean allowStudentScreenShare,
        Instant createdAt,
        Instant finishedAt
) {
}
