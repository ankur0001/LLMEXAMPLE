package com.projectmind.core.domain;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Represents a single file discovered during repository scan.
 */
public record RepositoryFile(
        Path relativePath,
        Path absolutePath,
        FileType fileType,
        String contentHash,
        long sizeBytes,
        Instant lastModified
) {
}
