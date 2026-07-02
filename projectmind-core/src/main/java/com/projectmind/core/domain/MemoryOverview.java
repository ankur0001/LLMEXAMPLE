package com.projectmind.core.domain;

import java.time.Instant;
import java.util.List;

/**
 * High-level view of persisted repository memory.
 */
public record MemoryOverview(
        ProjectMetadata metadata,
        int graphNodeCount,
        int graphEdgeCount,
        int fileSummaryCount,
        int packageSummaryCount,
        int classSummaryCount,
        int apiSummaryCount,
        int historyEntryCount,
        int cacheEntryCount,
        List<HistorySnapshot> recentHistory
) {
}
