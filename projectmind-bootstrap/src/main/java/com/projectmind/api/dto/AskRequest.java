package com.projectmind.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Question-answering request")
public record AskRequest(
        @NotBlank @Schema(example = "/Users/dev/my-app") String path,
        @NotBlank @Schema(example = "How does authentication work?") String question,
        @Schema(description = "Optional Ollama model name; uses auto-selected model when omitted",
                example = "phi3:latest")
        String model
) {
}
