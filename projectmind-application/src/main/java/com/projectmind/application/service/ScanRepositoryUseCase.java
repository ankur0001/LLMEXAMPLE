package com.projectmind.application.service;

import com.projectmind.core.domain.HistorySnapshot;
import com.projectmind.core.domain.ProgressCallback;
import com.projectmind.core.domain.ProjectMetadata;
import com.projectmind.core.port.ConfigurationPort;
import com.projectmind.core.port.MemoryManagerPort;
import com.projectmind.core.port.RepositoryScannerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Orchestrates full repository scanning and initial memory creation.
 */
public class ScanRepositoryUseCase {

    private static final Logger log = LoggerFactory.getLogger(ScanRepositoryUseCase.class);

    private final RepositoryScannerPort scanner;
    private final MemoryManagerPort memoryManager;
    private final RepositoryGraphBuilder graphBuilder;
    private final RepositoryVectorIndexer vectorIndexer;
    private final ConfigurationPort configuration;

    public ScanRepositoryUseCase(
            RepositoryScannerPort scanner,
            MemoryManagerPort memoryManager,
            RepositoryGraphBuilder graphBuilder,
            RepositoryVectorIndexer vectorIndexer,
            ConfigurationPort configuration) {
        this.scanner = scanner;
        this.memoryManager = memoryManager;
        this.graphBuilder = graphBuilder;
        this.vectorIndexer = vectorIndexer;
        this.configuration = configuration;
    }

    /**
     * Performs a full scan of the repository and persists the file index.
     *
     * @param repositoryPath absolute path to the repository
     * @param progress       progress callback
     * @return updated project metadata
     */
    public ProjectMetadata execute(Path repositoryPath, ProgressCallback progress) {
        log.info("Starting repository scan: {}", repositoryPath);
        ProjectMetadata metadata = ScanOrchestrator.runScan(
                repositoryPath, progress, List.of(), scanner, memoryManager, configuration);
        graphBuilder.buildAndPersist(repositoryPath);
        vectorIndexer.indexRepository(repositoryPath);
        memoryManager.saveHistorySnapshot(repositoryPath, new HistorySnapshot(
                Instant.now(),
                "scan",
                metadata.totalFiles(),
                "Repository scan completed"));
        return metadata;
    }
}
