package com.projectmind.adapter.vector;

import com.projectmind.adapter.config.ProjectMindProperties;
import com.projectmind.core.port.ConfigurationPort;
import com.projectmind.core.port.MemoryManagerPort;
import com.projectmind.core.port.OllamaClientPort;
import com.projectmind.core.port.VectorIndexPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the vector index backend (ChromaDB or in-memory fallback).
 */
@Configuration
public class VectorIndexConfiguration {

    @Bean
    VectorIndexPort vectorIndexPort(
            ProjectMindProperties config,
            OllamaClientPort ollamaClient,
            MemoryManagerPort memoryManager) {
        String backend = config.getVectorBackend();
        if ("memory".equalsIgnoreCase(backend)) {
            return new InMemoryVectorIndex(ollamaClient, memoryManager, config);
        }
        if ("chroma".equalsIgnoreCase(backend) || ChromaVectorIndex.isAvailable(config.getChromaUrl())) {
            return new ChromaVectorIndex(config, ollamaClient, memoryManager);
        }
        return new InMemoryVectorIndex(ollamaClient, memoryManager, config);
    }
}
