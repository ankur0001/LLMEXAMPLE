package com.projectmind.core.domain;

import java.util.List;

/**
 * AI-generated summary for a class or interface.
 */
public record ClassSummary(
        String qualifiedName,
        String summary,
        String role,
        List<String> responsibilities
) {
    public ClassSummary {
        responsibilities = responsibilities != null ? List.copyOf(responsibilities) : List.of();
    }
}
