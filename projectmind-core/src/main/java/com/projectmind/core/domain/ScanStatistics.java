package com.projectmind.core.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated counts and sizes from a repository scan.
 */
public record ScanStatistics(
        Map<FileType, Integer> countByType,
        long totalBytes
) {
    public ScanStatistics {
        countByType = countByType != null ? Map.copyOf(countByType) : Map.of();
    }

    public static ScanStatistics empty() {
        return new ScanStatistics(Map.of(), 0);
    }

    /**
     * Computes statistics from a list of scanned files.
     */
    public static ScanStatistics fromFiles(List<RepositoryFile> files) {
        Map<FileType, Integer> counts = new EnumMap<>(FileType.class);
        long bytes = 0;
        for (RepositoryFile file : files) {
            counts.merge(file.fileType(), 1, Integer::sum);
            bytes += file.sizeBytes();
        }
        return new ScanStatistics(counts, bytes);
    }

    public int countFor(FileType type) {
        return countByType.getOrDefault(type, 0);
    }
}
