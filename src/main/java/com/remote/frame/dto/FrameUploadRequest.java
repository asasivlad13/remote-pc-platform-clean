package com.remote.frame.dto;

import jakarta.validation.constraints.NotBlank;

public record FrameUploadRequest(
        @NotBlank
        String mac,

        @NotBlank
        String image
) {
}