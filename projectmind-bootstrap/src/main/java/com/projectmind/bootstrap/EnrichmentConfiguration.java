package com.projectmind.bootstrap;

import com.projectmind.application.service.RepositoryEnrichmentUseCase;
import com.projectmind.bootstrap.enrichment.AsyncPostScanEnrichment;
import com.projectmind.bootstrap.enrichment.AsyncRepositoryEnrichmentService;
import com.projectmind.core.port.PostScanEnrichmentPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class EnrichmentConfiguration {

    @Bean
    AsyncRepositoryEnrichmentService asyncRepositoryEnrichmentService(
            RepositoryEnrichmentUseCase enrichmentUseCase) {
        return new AsyncRepositoryEnrichmentService(enrichmentUseCase);
    }

    @Bean
    @Primary
    PostScanEnrichmentPort asyncPostScanEnrichment(
            AsyncRepositoryEnrichmentService enrichmentService) {
        return new AsyncPostScanEnrichment(enrichmentService);
    }
}
