package com.projectmind.application.service;

import com.projectmind.core.domain.FileChangeSet;
import com.projectmind.core.domain.RepositoryFile;
import com.projectmind.core.domain.RepositoryIndex;
import com.projectmind.core.domain.ScanStatistics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges detected file changes into a stored repository index.
 */
public final class RepositoryIndexMerger {

    private RepositoryIndexMerger() {
    }

    public static RepositoryIndex merge(RepositoryIndex stored, FileChangeSet changes, Instant updatedAt) {
        Map<String, RepositoryFile> filesByPath = new LinkedHashMap<>();
        for (RepositoryFile file : stored.files()) {
            filesByPath.put(file.relativePath().toString(), file);
        }
        for (String deleted : changes.deleted()) {
            filesByPath.remove(deleted);
        }
        for (RepositoryFile added : changes.added()) {
            filesByPath.put(added.relativePath().toString(), added);
        }
        for (RepositoryFile modified : changes.modified()) {
            filesByPath.put(modified.relativePath().toString(), modified);
        }

        List<RepositoryFile> merged = filesByPath.values().stream()
                .sorted(Comparator.comparing(file -> file.relativePath().toString()))
                .toList();
        ScanStatistics statistics = ScanStatistics.fromFiles(merged);
        return new RepositoryIndex(
                stored.repositoryPath(),
                updatedAt,
                merged.size(),
                merged,
                statistics,
                stored.scanDurationMs());
    }
}
