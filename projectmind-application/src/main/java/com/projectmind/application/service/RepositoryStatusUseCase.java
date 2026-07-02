package com.projectmind.application.service;

import com.projectmind.core.domain.ProjectMetadata;
import com.projectmind.core.domain.ScanStatus;
import com.projectmind.core.path.RepositoryPathResolver;
import com.projectmind.core.port.MemoryManagerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Returns current repository scan status and metadata.
 */
public class RepositoryStatusUseCase {

    private static final Logger log = LoggerFactory.getLogger(RepositoryStatusUseCase.class);

    private final MemoryManagerPort memoryManager;

    public RepositoryStatusUseCase(MemoryManagerPort memoryManager) {
        this.memoryManager = memoryManager;
    }

    /**
     * Returns project metadata if the repository has been scanned.
     */
    public Optional<ProjectMetadata> execute(Path repositoryPath) {
        Path resolvedPath = RepositoryPathResolver.resolve(repositoryPath);
        if (!memoryManager.loadMetadata(resolvedPath).isPresent()) {
            log.debug("No memory found for: {}", resolvedPath);
            return Optional.of(new ProjectMetadata(
                    resolvedPath.getFileName().toString(),
                    RepositoryPathResolver.toStorageKey(resolvedPath),
                    ScanStatus.NOT_SCANNED,
                    null, null, null, 0, 0,
                    null, java.util.Map.of()));
        }
        return memoryManager.loadMetadata(resolvedPath);
    }
}
