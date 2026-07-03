package com.projectmind.bootstrap.enrichment;

import com.projectmind.core.path.RepositoryPathResolver;
import com.projectmind.core.port.PostScanEnrichmentPort;

import java.nio.file.Path;

public class AsyncPostScanEnrichment implements PostScanEnrichmentPort {

    private final AsyncRepositoryEnrichmentService enrichmentService;

    public AsyncPostScanEnrichment(AsyncRepositoryEnrichmentService enrichmentService) {
        this.enrichmentService = enrichmentService;
    }

    @Override
    public void scheduleAfterScan(Path repositoryPath) {
        enrichmentService.queueAfterScan(repositoryPath);
    }
}
