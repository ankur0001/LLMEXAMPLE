package com.projectmind.core.domain;

import java.time.Instant;

/**
 * Checkpoint metadata for resumable repository scans.
 */
public record ScanCheckpoint(
        Instant startedAt,
        Instant lastUpdatedAt,
        int filesProcessed,
        int filesDiscovered,
        String lastProcessedPath,
        boolean complete
) {
}
