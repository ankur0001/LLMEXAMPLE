package com.projectmind.core.domain;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Complete inventory of files in a scanned repository.
 */
public record RepositoryIndex(
        Path repositoryPath,
        Instant scannedAt,
        int totalFiles,
        List<RepositoryFile> files,
        ScanStatistics statistics,
        long scanDurationMs
) {
    public RepositoryIndex {
        files = files != null ? List.copyOf(files) : List.of();
        statistics = statistics != null ? statistics : ScanStatistics.empty();
    }

    /**
     * Convenience constructor without statistics (computed from files).
     */
    public RepositoryIndex(Path repositoryPath, Instant scannedAt, int totalFiles, List<RepositoryFile> files) {
        this(repositoryPath, scannedAt, totalFiles, files, ScanStatistics.fromFiles(files), 0);
    }
}
