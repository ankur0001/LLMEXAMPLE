package com.projectmind.core.domain;

import java.util.List;

/**
 * AI-generated summary for a Java/Kotlin package.
 */
public record PackageSummary(
        String packageName,
        String summary,
        List<String> keyTypes
) {
    public PackageSummary {
        keyTypes = keyTypes != null ? List.copyOf(keyTypes) : List.of();
    }
}
