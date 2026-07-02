package com.projectmind.core.domain;

import java.time.Instant;
import java.util.Map;

/**
 * Top-level metadata for a ProjectMind-managed repository.
 */
public record ProjectMetadata(
        String name,
        String repositoryPath,
        ScanStatus status,
        Instant firstScannedAt,
        Instant lastScannedAt,
        Instant lastUpdatedAt,
        int totalFiles,
        int indexedFiles,
        String ollamaModel,
        Map<String, String> properties
) {
}
