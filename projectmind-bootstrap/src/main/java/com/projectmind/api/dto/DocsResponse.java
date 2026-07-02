package com.projectmind.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Documentation generation response")
public record DocsResponse(
        @Schema(description = "Path to generated index.html")
        String indexPath
) {
}
