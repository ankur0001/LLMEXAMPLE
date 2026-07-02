package com.projectmind.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Standard API error response")
public record ApiError(
        @Schema(example = "400") int status,
        @Schema(example = "Bad Request") String error,
        @Schema(example = "path must not be blank") String message,
        @Schema(description = "Field-level validation errors when applicable")
        List<String> details,
        @Schema(example = "2026-07-02T12:00:00Z") Instant timestamp
) {
    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, List.of(), Instant.now());
    }

    public static ApiError of(int status, String error, String message, List<String> details) {
        return new ApiError(status, error, message, details, Instant.now());
    }
}
