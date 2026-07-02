package com.projectmind.adapter.incremental;

import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.RepositoryFile;
import com.projectmind.core.domain.RepositoryIndex;
import com.projectmind.core.port.RepositoryScannerPort;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HashBasedIncrementalUpdateTest {

    @Test
    void detectsAddedModifiedAndDeletedFiles() {
        RepositoryScannerPort scanner = mock(RepositoryScannerPort.class);
        HashBasedIncrementalUpdate update = new HashBasedIncrementalUpdate(scanner);

        Path repo = Path.of("/tmp/repo");
        RepositoryIndex stored = new RepositoryIndex(
                repo,
                Instant.now(),
                2,
                List.of(
                        file("App.java", "hash-a"),
                        file("Removed.java", "hash-r")));

        when(scanner.scanStream(eq(repo), any(Map.class))).thenReturn(Stream.of(
                file("App.java", "hash-b"),
                file("New.java", "hash-n")));

        var changes = update.detectChanges(repo, stored);

        assertThat(changes.added()).extracting(f -> f.relativePath().toString()).containsExactly("New.java");
        assertThat(changes.modified()).extracting(f -> f.relativePath().toString()).containsExactly("App.java");
        assertThat(changes.deleted()).containsExactly("Removed.java");
        assertThat(changes.totalChanges()).isEqualTo(3);
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
