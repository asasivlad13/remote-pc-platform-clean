package com.remote.frame.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record FrameUploadRequest(
        @NotNull
        @Positive
        Long pcId,

        @NotBlank
        String image
) {
}