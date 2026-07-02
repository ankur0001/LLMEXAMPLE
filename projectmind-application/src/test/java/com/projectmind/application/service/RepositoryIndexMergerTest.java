package com.projectmind.application.service;

import com.projectmind.core.domain.FileChangeSet;
import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.RepositoryFile;
import com.projectmind.core.domain.RepositoryIndex;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryIndexMergerTest {

    @Test
    void mergesAddedModifiedAndDeletedFiles() {
        Path repo = Path.of("/tmp/repo");
        Instant scannedAt = Instant.parse("2026-01-01T00:00:00Z");
        RepositoryIndex stored = new RepositoryIndex(
                repo,
                scannedAt,
                2,
                List.of(
                        file("App.java", "hash-a"),
                        file("Old.java", "hash-old")));

        RepositoryFile modified = file("App.java", "hash-b");
        RepositoryFile added = file("New.java", "hash-n");
        FileChangeSet changes = new FileChangeSet(
                List.of(added),
                List.of(modified),
                List.of("Old.java"));

        RepositoryIndex merged = RepositoryIndexMerger.merge(stored, changes, Instant.now());

        assertThat(merged.totalFiles()).isEqualTo(2);
        assertThat(merged.files()).extracting(f -> f.relativePath().toString())
                .containsExactlyInAnyOrder("App.java", "New.java");
        assertThat(merged.files().stream()
                .filter(f -> f.relativePath().toString().equals("App.java"))
                .findFirst()
                .orElseThrow()
                .contentHash()).isEqualTo("hash-b");
    }

    private static RepositoryFile file(String path, String hash) {
        return new RepositoryFile(
                Path.of(path),
                Path.of("/tmp/repo").resolve(path),
                FileType.JAVA,
                hash,
                100,
                Instant.now());
    }
}
