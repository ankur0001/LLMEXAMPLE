package com.projectmind.core.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Result of comparing current repository state against stored index.
 */
public record FileChangeSet(
        List<RepositoryFile> added,
        List<RepositoryFile> modified,
        List<String> deleted
) {
    public FileChangeSet {
        added = added != null ? List.copyOf(added) : List.of();
        modified = modified != null ? List.copyOf(modified) : List.of();
        deleted = deleted != null ? List.copyOf(deleted) : List.of();
    }

    public boolean hasChanges() {
        return !added.isEmpty() || !modified.isEmpty() || !deleted.isEmpty();
    }

    public int totalChanges() {
        return added.size() + modified.size() + deleted.size();
    }

    /** Files that were added or modified and need re-processing. */
    public List<RepositoryFile> changedFiles() {
        List<RepositoryFile> changed = new ArrayList<>(added.size() + modified.size());
        changed.addAll(added);
        changed.addAll(modified);
        return List.copyOf(changed);
    }

    /** All affected relative paths including deletions. */
    public List<String> affectedPaths() {
        return Stream.concat(
                changedFiles().stream().map(file -> file.relativePath().toString()),
                deleted.stream())
                .distinct()
                .toList();
    }

    /** Treat every indexed file as newly added (full-repository summarization). */
    public static FileChangeSet allIndexedFiles(List<RepositoryFile> files) {
        return new FileChangeSet(files != null ? files : List.of(), List.of(), List.of());
    }
}
