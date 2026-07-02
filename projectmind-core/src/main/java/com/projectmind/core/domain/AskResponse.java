package com.projectmind.core.domain;

import java.util.List;

/**
 * An AI-generated answer with source citations.
 */
public record AskResponse(
        String question,
        String answer,
        List<String> sourceFiles,
        String model
) {
    public AskResponse {
        sourceFiles = sourceFiles != null ? List.copyOf(sourceFiles) : List.of();
    }
}
