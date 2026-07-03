package com.projectmind.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Post-scan enrichment and documentation generation status")
public record EnrichmentResponse(
        @Schema(description = "PENDING, RUNNING, READY, or FAILED")
        String status,
        @Schema(description = "Human-readable progress message")
        String message,
        @Schema(description = "Path to generated index.html when ready")
        String docsPath,
        @Schema(description = "Number of files summarized in the latest enrichment run")
        int filesSummarized
) {
}
