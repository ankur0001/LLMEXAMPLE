package com.projectmind.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Memory export response")
public record ExportResponse(
        @Schema(description = "Path to exported .ai-memory directory")
        String exportPath
) {
}
