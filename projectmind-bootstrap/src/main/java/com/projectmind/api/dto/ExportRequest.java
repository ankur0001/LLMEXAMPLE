package com.projectmind.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Export memory request")
public record ExportRequest(
        @NotBlank @Schema(example = "/Users/dev/my-app") String path,
        @Schema(description = "Output directory; defaults to current directory", example = "/tmp/export")
        String outputDir
) {
}
