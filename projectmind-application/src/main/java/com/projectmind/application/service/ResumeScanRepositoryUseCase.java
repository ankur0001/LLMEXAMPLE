package com.projectmind.application.service;

import com.projectmind.core.domain.HistorySnapshot;
import com.projectmind.core.domain.ProgressCallback;
import com.projectmind.core.domain.ProjectMetadata;
import com.projectmind.core.domain.RepositoryFile;
import com.projectmind.core.domain.ScanStatus;
import com.projectmind.core.port.ConfigurationPort;
import com.projectmind.core.port.MemoryManagerPort;
import com.projectmind.core.port.RepositoryScannerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Resumes an interrupted repository scan from saved progress.
 */
public class ResumeScanRepositoryUseCase {

    private static final Logger log = LoggerFactory.getLogger(ResumeScanRepositoryUseCase.class);

    private final RepositoryScannerPort scanner;
    private final MemoryManagerPort memoryManager;
    private final RepositoryGraphBuilder graphBuilder;
    private final RepositoryVectorIndexer vectorIndexer;
    private final ConfigurationPort configuration;

    public ResumeScanRepositoryUseCase(
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
     * Resumes scanning from {@code scan_progress.json} if present.
     *
     * @param repositoryPath absolute path to the repository
     * @param progress       progress callback
     * @return updated project metadata
     */
    public ProjectMetadata execute(Path repositoryPath, ProgressCallback progress) {
        var metadata = memoryManager.loadMetadata(repositoryPath)
                .orElseThrow(() -> new IllegalStateException(
                        "No scan in progress. Run 'projectmind scan' first."));

        if (metadata.status() != ScanStatus.SCANNING && metadata.status() != ScanStatus.INTERRUPTED) {
            var progressFiles = memoryManager.loadScanProgress(repositoryPath);
            if (progressFiles.isEmpty()) {
                throw new IllegalStateException(
                        "Nothing to resume. Repository status: " + metadata.status());
            }
        }

        List<RepositoryFile> existing = memoryManager.loadScanProgress(repositoryPath)
                .map(index -> index.files())
                .orElse(List.of());

        log.info("Resuming repository scan: {} ({} files already indexed)",
                repositoryPath, existing.size());

        ProjectMetadata completed = ScanOrchestrator.runScan(
                repositoryPath, progress, existing, scanner, memoryManager, configuration);
        graphBuilder.buildAndPersist(repositoryPath);
        vectorIndexer.indexRepository(repositoryPath);
        memoryManager.saveHistorySnapshot(repositoryPath, new HistorySnapshot(
                Instant.now(),
                "resume",
                completed.totalFiles(),
                "Resumed repository scan completed"));
        return completed;
    }
}
