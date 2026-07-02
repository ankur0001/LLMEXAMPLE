package com.projectmind.application.service;

import com.projectmind.core.domain.MemoryOverview;
import com.projectmind.core.domain.ProjectMetadata;
import com.projectmind.core.domain.ScanStatus;
import com.projectmind.core.path.RepositoryPathResolver;
import com.projectmind.core.port.MemoryManagerPort;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Returns a high-level overview of persisted repository memory.
 */
public class MemoryOverviewUseCase {

    private final MemoryManagerPort memoryManager;

    public MemoryOverviewUseCase(MemoryManagerPort memoryManager) {
        this.memoryManager = memoryManager;
    }

    public Optional<MemoryOverview> execute(Path repositoryPath) {
        Path resolvedPath = RepositoryPathResolver.resolve(repositoryPath);
        Optional<MemoryOverview> overview = memoryManager.loadOverview(resolvedPath);
        if (overview.isPresent()) {
            return overview;
        }

        if (memoryManager.loadMetadata(resolvedPath).isPresent()) {
            return Optional.empty();
        }

        return Optional.of(emptyOverview(resolvedPath));
    }

    private static MemoryOverview emptyOverview(Path repositoryPath) {
        String canonicalPath = RepositoryPathResolver.toStorageKey(repositoryPath);
        ProjectMetadata metadata = new ProjectMetadata(
                repositoryPath.getFileName().toString(),
                canonicalPath,
                ScanStatus.NOT_SCANNED,
                null,
                null,
                null,
                0,
                0,
                null,
                Map.of());
        return new MemoryOverview(metadata, 0, 0, 0, 0, 0, 0, 0, 0, List.of());
    }
}
