package com.projectmind.core.domain;

/**
 * A semantic search result from the vector index.
 */
public record SearchResult(
        String relativePath,
        String snippet,
        double score,
        FileType fileType
) {
}
