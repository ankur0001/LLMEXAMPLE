package com.projectmind.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Installed Ollama model")
public record OllamaModelDto(
        @Schema(example = "phi3:latest") String name,
        @Schema(example = "[\"completion\"]") List<String> capabilities
) {
}
