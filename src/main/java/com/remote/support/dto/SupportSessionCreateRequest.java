package com.remote.support.dto;

import jakarta.validation.constraints.Size;

public record SupportSessionCreateRequest(
        @Size(max = 150)
        String title
) {
}