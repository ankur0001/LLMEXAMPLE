package com.projectmind.core.domain;

import java.time.Instant;
import java.util.List;

/**
 * Point-in-time record of a repository memory operation.
 */
public record HistorySnapshot(
        Instant timestamp,
        String operation,
        int filesChanged,
        String description,
        List<String> affectedFiles
) {
    public HistorySnapshot(Instant timestamp, String operation, int filesChanged, String description) {
        this(timestamp, operation, filesChanged, description, List.of());
    }

    public HistorySnapshot {
        affectedFiles = affectedFiles != null ? List.copyOf(affectedFiles) : List.of();
    }
}
