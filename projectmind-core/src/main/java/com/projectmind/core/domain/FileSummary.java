package com.projectmind.core.domain;

import java.util.List;

/**
 * AI-generated summary for a source file.
 */
public record FileSummary(
        String relativePath,
        String summary,
        String purpose,
        List<String> keyConcepts
) {
    public FileSummary {
        keyConcepts = keyConcepts != null ? List.copyOf(keyConcepts) : List.of();
    }
}
