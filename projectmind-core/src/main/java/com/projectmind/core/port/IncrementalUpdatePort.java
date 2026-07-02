package com.projectmind.core.port;

import com.projectmind.core.domain.FileChangeSet;
import com.projectmind.core.domain.RepositoryIndex;

import java.nio.file.Path;

/**
 * Port for detecting file changes and driving incremental updates.
 */
public interface IncrementalUpdatePort {

    /**
     * Compares current repository state against the stored index.
     *
     * @param repositoryPath repository root
     * @param storedIndex      previously persisted index
     * @return set of added, modified, and deleted files
     */
    FileChangeSet detectChanges(Path repositoryPath, RepositoryIndex storedIndex);
}
