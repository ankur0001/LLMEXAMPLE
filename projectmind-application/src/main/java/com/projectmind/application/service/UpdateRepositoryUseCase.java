package com.projectmind.application.service;

import com.projectmind.core.domain.FileChangeSet;
import com.projectmind.core.domain.HistorySnapshot;
import com.projectmind.core.domain.ProgressCallback;
import com.projectmind.core.domain.ProjectMetadata;
import com.projectmind.core.domain.ScanStatus;
import com.projectmind.core.port.IncrementalUpdatePort;
import com.projectmind.core.port.MemoryManagerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Orchestrates incremental repository updates by detecting and processing changes.
 */
public class UpdateRepositoryUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateRepositoryUseCase.class);

    private final IncrementalUpdatePort incrementalUpdate;
    private final MemoryManagerPort memoryManager;
    private final RepositoryGraphBuilder graphBuilder;
    private final RepositoryVectorIndexer vectorIndexer;
    private final RepositorySummaryGenerator summaryGenerator;
    private final GenerateDocumentationUseCase documentationUseCase;

    public UpdateRepositoryUseCase(
            IncrementalUpdatePort incrementalUpdate,
            MemoryManagerPort memoryManager,
            RepositoryGraphBuilder graphBuilder,
            RepositoryVectorIndexer vectorIndexer,
            RepositorySummaryGenerator summaryGenerator,
            GenerateDocumentationUseCase documentationUseCase) {
        this.incrementalUpdate = incrementalUpdate;
        this.memoryManager = memoryManager;
        this.graphBuilder = graphBuilder;
        this.vectorIndexer = vectorIndexer;
        this.summaryGenerator = summaryGenerator;
        this.documentationUseCase = documentationUseCase;
    }

    /**
     * Detects changes and updates only modified knowledge.
     */
    public FileChangeSet execute(Path repositoryPath, ProgressCallback progress) {
        log.info("Starting incremental update: {}", repositoryPath);
        progress.onProgress("update", 0, 5, "Detecting changes");

        var storedIndex = memoryManager.loadIndex(repositoryPath)
                .orElseThrow(() -> new IllegalStateException(
                        "No existing index found. Run 'projectmind scan' first."));

        var metadata = memoryManager.loadMetadata(repositoryPath)
                .orElseThrow(() -> new IllegalStateException("No project metadata found."));

        memoryManager.saveMetadata(repositoryPath, updatingMetadata(metadata));

        try {
            FileChangeSet changes = incrementalUpdate.detectChanges(repositoryPath, storedIndex);

            if (changes.hasChanges()) {
                log.info("Detected {} changes ({} added, {} modified, {} deleted)",
                        changes.totalChanges(),
                        changes.added().size(),
                        changes.modified().size(),
                        changes.deleted().size());

                progress.onProgress("update", 1, 5, "Merging index");
                var mergedIndex = RepositoryIndexMerger.merge(storedIndex, changes, Instant.now());
                memoryManager.saveIndex(repositoryPath, mergedIndex);

                progress.onProgress("update", 2, 5, "Re-parsing changed files");
                graphBuilder.updateChangedFiles(repositoryPath, changes);

                progress.onProgress("update", 3, 5, "Re-summarizing changed files");
                summaryGenerator.summarizeChanges(repositoryPath, changes);

                progress.onProgress("update", 4, 5, "Re-embedding changed files");
                vectorIndexer.updateChangedFiles(repositoryPath, changes);

                progress.onProgress("update", 5, 5, "Regenerating documentation");
                if (memoryManager.loadDocumentationSections(repositoryPath).isPresent()) {
                    documentationUseCase.regenerateChanged(repositoryPath, changes.affectedPaths());
                }

                memoryManager.saveMetadata(repositoryPath, indexedMetadata(metadata, mergedIndex.totalFiles()));
                memoryManager.saveHistorySnapshot(repositoryPath, new HistorySnapshot(
                        Instant.now(),
                        "update",
                        changes.totalChanges(),
                        "Incremental update: "
                                + changes.added().size() + " added, "
                                + changes.modified().size() + " modified, "
                                + changes.deleted().size() + " deleted",
                        changes.affectedPaths()));
            } else {
                log.info("No changes detected");
                memoryManager.saveMetadata(repositoryPath, indexedMetadata(metadata, metadata.totalFiles()));
            }

            return changes;
        } catch (RuntimeException e) {
            memoryManager.saveMetadata(repositoryPath, indexedMetadata(metadata, metadata.totalFiles()));
            throw e;
        }
    }

    private static ProjectMetadata updatingMetadata(ProjectMetadata metadata) {
        return new ProjectMetadata(
                metadata.name(), metadata.repositoryPath(), ScanStatus.UPDATING,
                metadata.firstScannedAt(), metadata.lastScannedAt(), Instant.now(),
                metadata.totalFiles(), metadata.indexedFiles(),
                metadata.ollamaModel(), metadata.properties());
    }

    private static ProjectMetadata indexedMetadata(ProjectMetadata metadata, int totalFiles) {
        return new ProjectMetadata(
                metadata.name(), metadata.repositoryPath(), ScanStatus.INDEXED,
                metadata.firstScannedAt(), metadata.lastScannedAt(), Instant.now(),
                totalFiles, totalFiles,
                metadata.ollamaModel(), metadata.properties());
    }
}
