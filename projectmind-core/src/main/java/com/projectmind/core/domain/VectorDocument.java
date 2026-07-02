package com.projectmind.core.domain;

/**
 * A chunk of text stored in the vector index.
 */
public record VectorDocument(
        String id,
        String relativePath,
        String content,
        String summary,
        FileType fileType
) {
}
