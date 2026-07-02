package com.projectmind.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Installed Ollama models")
public record OllamaModelsResponse(
        @Schema(description = "Models returned by the local Ollama /api/tags endpoint")
        List<OllamaModelDto> models
) {
}
