package com.remote.education.dto;

public record EducationSessionStatisticsResponse(
        String sessionCode,
        String title,
        String status,

        long studentsTotal,
        long approvedStudents,
        long waitingStudents,
        long rejectedStudents,
        long leftStudents,

        long studentsWithControl,
        long activeScreenShares,
        long requestedScreenShares,

        long filesTransferred,
        long totalFileSizeBytes,

        long eventsCount
) {
}