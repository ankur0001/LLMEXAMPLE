package com.projectmind.core.port;

import com.projectmind.core.domain.ProgressCallback;
import com.projectmind.core.domain.RepositoryFile;
import com.projectmind.core.domain.RepositoryIndex;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Port for recursively scanning repository filesystems.
 */
public interface RepositoryScannerPort {

    /**
     * Scans a repository and returns an index of all discovered files.
     *
     * @param repositoryPath absolute path to the repository root
     * @param progress       callback for scan progress updates
     * @return complete repository file index
     */
    RepositoryIndex scan(Path repositoryPath, ProgressCallback progress);

    /**
     * Scans a repository, skipping files already indexed (for resume).
     */
    default RepositoryIndex scan(Path repositoryPath, ProgressCallback progress, Set<String> skipRelativePaths) {
        return scan(repositoryPath, progress, skipRelativePaths, null);
    }

    /**
     * Scans a repository with optional batch snapshots for checkpoint persistence.
     *
     * @param batchSnapshot invoked after each batch with cumulative newly scanned files; may be null
     */
    RepositoryIndex scan(
            Path repositoryPath,
            ProgressCallback progress,
            Set<String> skipRelativePaths,
            Consumer<List<RepositoryFile>> batchSnapshot);

    /**
     * Returns a lazy stream of files without building a full index.
     * Used for large repositories to avoid loading everything into memory.
     *
     * @param repositoryPath absolute path to the repository root
     * @return stream of discovered files
     */
    Stream<RepositoryFile> scanStream(Path repositoryPath);

    /**
     * Returns a lazy stream of files, reusing stored hashes when size and mtime are unchanged.
     */
    default Stream<RepositoryFile> scanStream(Path repositoryPath, java.util.Map<String, RepositoryFile> storedByPath) {
        return scanStream(repositoryPath);
    }
}
