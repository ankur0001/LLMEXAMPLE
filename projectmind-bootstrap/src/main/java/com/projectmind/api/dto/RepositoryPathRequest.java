package com.projectmind.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Repository path request")
public record RepositoryPathRequest(
        @NotBlank @Schema(description = "Absolute or relative repository root path", example = "/Users/dev/my-app")
        String path
) {
}
