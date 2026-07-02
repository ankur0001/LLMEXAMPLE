package com.projectmind;

import com.projectmind.application.service.AskQuestionUseCase;
import com.projectmind.application.service.ExportRepositoryUseCase;
import com.projectmind.application.service.GenerateDocumentationUseCase;
import com.projectmind.application.service.GraphQueryUseCase;
import com.projectmind.application.service.MemoryOverviewUseCase;
import com.projectmind.application.service.PluginEnhancementService;
import com.projectmind.application.service.RepositoryGraphBuilder;
import com.projectmind.application.service.RepositorySummaryGenerator;
import com.projectmind.application.service.RepositoryStatusUseCase;
import com.projectmind.application.service.RepositoryVectorIndexer;
import com.projectmind.application.service.ResumeScanRepositoryUseCase;
import com.projectmind.application.service.ScanRepositoryUseCase;
import com.projectmind.application.service.UpdateRepositoryUseCase;
import com.projectmind.core.port.ConfigurationPort;
import com.projectmind.core.port.DocumentationGeneratorPort;
import com.projectmind.core.port.HtmlGeneratorPort;
import com.projectmind.core.port.IncrementalUpdatePort;
import com.projectmind.core.port.KnowledgeGraphPort;
import com.projectmind.core.port.LanguageParserPort;
import com.projectmind.core.port.MemoryManagerPort;
import com.projectmind.core.port.OllamaClientPort;
import com.projectmind.core.port.PluginRegistryPort;
import com.projectmind.core.port.RepositoryScannerPort;
import com.projectmind.core.port.VectorIndexPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires application use cases to port implementations via dependency injection.
 */
@Configuration
public class UseCaseConfiguration {

    @Bean
    PluginEnhancementService pluginEnhancementService(PluginRegistryPort pluginRegistry) {
        return new PluginEnhancementService(pluginRegistry);
    }

    @Bean
    RepositoryGraphBuilder repositoryGraphBuilder(
            LanguageParserPort languageParser,
            KnowledgeGraphPort knowledgeGraphPort,
            MemoryManagerPort memoryManager,
            PluginEnhancementService pluginEnhancementService,
            ConfigurationPort configuration) {
        return new RepositoryGraphBuilder(
                languageParser, knowledgeGraphPort, memoryManager, pluginEnhancementService, configuration);
    }

    @Bean
    RepositoryVectorIndexer repositoryVectorIndexer(
            VectorIndexPort vectorIndex,
            MemoryManagerPort memoryManager,
            ConfigurationPort configuration) {
        return new RepositoryVectorIndexer(vectorIndex, memoryManager, configuration);
    }

    @Bean
    ScanRepositoryUseCase scanRepositoryUseCase(
            RepositoryScannerPort scanner,
            MemoryManagerPort memoryManager,
            RepositoryGraphBuilder graphBuilder,
            RepositoryVectorIndexer vectorIndexer,
            ConfigurationPort configuration) {
        return new ScanRepositoryUseCase(scanner, memoryManager, graphBuilder, vectorIndexer, configuration);
    }

    @Bean
    ResumeScanRepositoryUseCase resumeScanRepositoryUseCase(
            RepositoryScannerPort scanner,
            MemoryManagerPort memoryManager,
            RepositoryGraphBuilder graphBuilder,
            RepositoryVectorIndexer vectorIndexer,
            ConfigurationPort configuration) {
        return new ResumeScanRepositoryUseCase(
                scanner, memoryManager, graphBuilder, vectorIndexer, configuration);
    }

    @Bean
    RepositorySummaryGenerator repositorySummaryGenerator(
            OllamaClientPort ollamaClient,
            MemoryManagerPort memoryManager,
            ConfigurationPort configuration) {
        return new RepositorySummaryGenerator(ollamaClient, memoryManager, configuration);
    }

    @Bean
    UpdateRepositoryUseCase updateRepositoryUseCase(
            IncrementalUpdatePort incrementalUpdate,
            MemoryManagerPort memoryManager,
            RepositoryGraphBuilder graphBuilder,
            RepositoryVectorIndexer vectorIndexer,
            RepositorySummaryGenerator summaryGenerator,
            GenerateDocumentationUseCase documentationUseCase) {
        return new UpdateRepositoryUseCase(
                incrementalUpdate,
                memoryManager,
                graphBuilder,
                vectorIndexer,
                summaryGenerator,
                documentationUseCase);
    }

    @Bean
    GenerateDocumentationUseCase generateDocumentationUseCase(
            MemoryManagerPort memoryManager,
            DocumentationGeneratorPort docGenerator,
            HtmlGeneratorPort htmlGenerator,
            PluginRegistryPort pluginRegistry) {
        return new GenerateDocumentationUseCase(memoryManager, docGenerator, htmlGenerator, pluginRegistry);
    }

    @Bean
    AskQuestionUseCase askQuestionUseCase(
            VectorIndexPort vectorIndex,
            OllamaClientPort ollamaClient,
            MemoryManagerPort memoryManager) {
        return new AskQuestionUseCase(vectorIndex, ollamaClient, memoryManager);
    }

    @Bean
    RepositoryStatusUseCase repositoryStatusUseCase(MemoryManagerPort memoryManager) {
        return new RepositoryStatusUseCase(memoryManager);
    }

    @Bean
    GraphQueryUseCase graphQueryUseCase(MemoryManagerPort memoryManager, KnowledgeGraphPort knowledgeGraphPort) {
        return new GraphQueryUseCase(memoryManager, knowledgeGraphPort);
    }

    @Bean
    ExportRepositoryUseCase exportRepositoryUseCase(MemoryManagerPort memoryManager) {
        return new ExportRepositoryUseCase(memoryManager);
    }

    @Bean
    MemoryOverviewUseCase memoryOverviewUseCase(MemoryManagerPort memoryManager) {
        return new MemoryOverviewUseCase(memoryManager);
    }
}
