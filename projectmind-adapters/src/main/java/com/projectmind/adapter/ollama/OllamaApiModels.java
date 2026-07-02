package com.projectmind.adapter.ollama;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * JSON DTOs for the Ollama HTTP API.
 */
final class OllamaApiModels {

    private OllamaApiModels() {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record GenerateRequest(String model, String prompt, boolean stream) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record GenerateResponse(String response, boolean done) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record EmbeddingsRequest(String model, String prompt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingsResponse(List<Double> embedding) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TagsResponse(List<ModelInfo> models) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ModelInfo(@JsonAlias("model") String name, List<String> capabilities) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorResponse(String error) {
    }
}
