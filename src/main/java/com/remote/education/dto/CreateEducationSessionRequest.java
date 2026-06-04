package com.remote.education.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEducationSessionRequest(
        @NotNull
        Long teacherPcId,

        @NotBlank
        @Size(max = 150)
        String title,

        @Size(max = 100)
        String teacherDisplayName,

        @Min(1)
        @Max(100)
        Integer maxStudents,

        Boolean allowStudentControl,
        Boolean allowFileTransfer,
        Boolean allowStudentScreenShare,

        @Size(max = 100)
        String displayName
) {
}
