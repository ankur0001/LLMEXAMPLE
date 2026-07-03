package com.projectmind.bootstrap.enrichment;

import com.projectmind.application.service.RepositoryEnrichmentUseCase;
import com.projectmind.core.domain.EnrichmentMetadata;
import com.projectmind.core.path.RepositoryPathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

import java.nio.file.Path;

/**
 * Runs post-scan summarization and HTML documentation generation in the background.
 */
public class AsyncRepositoryEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(AsyncRepositoryEnrichmentService.class);

    private final RepositoryEnrichmentUseCase enrichmentUseCase;

    public AsyncRepositoryEnrichmentService(RepositoryEnrichmentUseCase enrichmentUseCase) {
        this.enrichmentUseCase = enrichmentUseCase;
    }

    public void queueAfterScan(Path repositoryPath) {
        Path resolved = RepositoryPathResolver.resolve(repositoryPath);
        enrichmentUseCase.markPending(resolved);
        log.info("Queued background enrichment for {}", resolved);
        scheduleAsync(resolved);
    }

    @Async("enrichmentExecutor")
    public void scheduleAsync(Path repositoryPath) {
        run(RepositoryPathResolver.resolve(repositoryPath));
    }

    void run(Path resolved) {
        try {
            enrichmentUseCase.execute(resolved);
        } catch (RuntimeException ex) {
            log.error("Background enrichment failed for {}: {}", resolved, ex.getMessage());
        }
    }

    public EnrichmentMetadata.Status status(Path repositoryPath) {
        return enrichmentUseCase.currentStatus(RepositoryPathResolver.resolve(repositoryPath));
    }
}
