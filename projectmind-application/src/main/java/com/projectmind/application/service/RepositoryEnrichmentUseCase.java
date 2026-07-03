package com.projectmind.application.service;

import com.projectmind.core.domain.EnrichmentMetadata;
import com.projectmind.core.domain.ProgressCallback;
import com.projectmind.core.domain.ProjectMetadata;
import com.projectmind.core.domain.RepositoryIndex;
import com.projectmind.core.port.MemoryManagerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * After indexing, summarizes every file and renders section-wise HTML documentation.
 */
public class RepositoryEnrichmentUseCase {

    private static final Logger log = LoggerFactory.getLogger(RepositoryEnrichmentUseCase.class);

    private final MemoryManagerPort memoryManager;
    private final RepositorySummaryGenerator summaryGenerator;
    private final GenerateDocumentationUseCase documentationUseCase;

    public RepositoryEnrichmentUseCase(
            MemoryManagerPort memoryManager,
            RepositorySummaryGenerator summaryGenerator,
            GenerateDocumentationUseCase documentationUseCase) {
        this.memoryManager = memoryManager;
        this.summaryGenerator = summaryGenerator;
        this.documentationUseCase = documentationUseCase;
    }

    public void execute(Path repositoryPath) {
        execute(repositoryPath, ProgressCallback.noop());
    }

    public void execute(Path repositoryPath, ProgressCallback progress) {
        ProjectMetadata metadata = memoryManager.loadMetadata(repositoryPath)
                .orElseThrow(() -> new IllegalStateException("Repository not scanned."));
        RepositoryIndex index = memoryManager.loadIndex(repositoryPath)
                .orElseThrow(() -> new IllegalStateException("No file index found."));

        markStatus(metadata, repositoryPath, EnrichmentMetadata.Status.RUNNING, null, 0,
                "Summarizing source files...");

        try {
            progress.onProgress("enrichment", 1, 3, "Summarizing source files");
            int summarized = summaryGenerator.summarizeRepository(repositoryPath, index);
            markStatus(metadata, repositoryPath, EnrichmentMetadata.Status.RUNNING, null, summarized,
                    "Generating section-wise HTML documentation...");

            progress.onProgress("enrichment", 2, 3, "Generating HTML documentation");
            Path docsPath = documentationUseCase.execute(repositoryPath);

            markStatus(metadata, repositoryPath, EnrichmentMetadata.Status.READY, docsPath.toString(),
                    summarized, "Documentation ready");
            log.info("Post-scan enrichment complete for {} ({} summaries, docs at {})",
                    repositoryPath, summarized, docsPath);
        } catch (RuntimeException ex) {
            markStatus(metadata, repositoryPath, EnrichmentMetadata.Status.FAILED, null, 0, ex.getMessage());
            throw ex;
        }
    }

    public EnrichmentMetadata.Status currentStatus(Path repositoryPath) {
        return memoryManager.loadMetadata(repositoryPath)
                .map(this::readStatus)
                .orElse(EnrichmentMetadata.Status.PENDING);
    }

    public void markPending(Path repositoryPath) {
        memoryManager.loadMetadata(repositoryPath).ifPresent(metadata ->
                markStatus(metadata, repositoryPath, EnrichmentMetadata.Status.PENDING, null, 0,
                        "Queued for file summarization and HTML documentation"));
    }

    private EnrichmentMetadata.Status readStatus(ProjectMetadata metadata) {
        String value = metadata.properties().get(EnrichmentMetadata.STATUS);
        if (value == null || value.isBlank()) {
            return EnrichmentMetadata.Status.PENDING;
        }
        try {
            return EnrichmentMetadata.Status.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return EnrichmentMetadata.Status.PENDING;
        }
    }

    private void markStatus(
            ProjectMetadata metadata,
            Path repositoryPath,
            EnrichmentMetadata.Status status,
            String docsPath,
            int filesSummarized,
            String message) {
        Map<String, String> properties = new HashMap<>(metadata.properties());
        properties.put(EnrichmentMetadata.STATUS, status.name());
        properties.put(EnrichmentMetadata.FILES_SUMMARIZED, String.valueOf(filesSummarized));
        properties.put(EnrichmentMetadata.MESSAGE, message != null ? message : "");
        if (docsPath != null) {
            properties.put(EnrichmentMetadata.DOCS_PATH, docsPath);
        } else {
            properties.remove(EnrichmentMetadata.DOCS_PATH);
        }

        ProjectMetadata updated = new ProjectMetadata(
                metadata.name(),
                metadata.repositoryPath(),
                metadata.status(),
                metadata.firstScannedAt(),
                metadata.lastScannedAt(),
                metadata.lastUpdatedAt(),
                metadata.totalFiles(),
                metadata.indexedFiles(),
                metadata.ollamaModel(),
                Map.copyOf(properties));
        memoryManager.saveMetadata(repositoryPath, updated);
    }
}
