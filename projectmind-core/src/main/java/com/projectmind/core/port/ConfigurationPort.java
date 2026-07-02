package com.projectmind.core.port;

import java.util.Optional;

/**
 * Port for application configuration management.
 */
public interface ConfigurationPort {

    String getOllamaBaseUrl();

    String getOllamaModel();

    /**
     * Request timeout in seconds for Ollama API calls.
     */
    default int getOllamaTimeoutSeconds() {
        return 120;
    }

    /**
     * Maximum retry attempts for transient Ollama failures.
     */
    default int getOllamaMaxRetries() {
        return 2;
    }

    /**
     * Model used for embedding requests.
     */
    default String getOllamaEmbedModel() {
        return "nomic-embed-text";
    }

    default int getVectorChunkSize() {
        return 1500;
    }

    default int getVectorChunkOverlap() {
        return 200;
    }

    default int getVectorBatchSize() {
        return 32;
    }

    default String getVectorBackend() {
        return "auto";
    }

    String getChromaUrl();

    String getGlobalDbPath();

    int getScanBatchSize();

    java.util.List<String> getSkipDirectories();

    String getDocsOutputDir();

    /**
     * Maximum parallel tasks for scan hashing.
     */
    default int getScanHashConcurrency() {
        return Math.max(2, Runtime.getRuntime().availableProcessors());
    }

    /**
     * Maximum parallel tasks for source file parsing.
     */
    default int getParseConcurrency() {
        return Math.max(2, Runtime.getRuntime().availableProcessors());
    }

    /**
     * Maximum parallel Ollama embedding requests per batch.
     */
    default int getOllamaEmbedConcurrency() {
        return 4;
    }

    /**
     * Maximum parallel Ollama summary requests during incremental update.
     */
    default int getSummaryConcurrency() {
        return 4;
    }

    /**
     * Persist scan checkpoints every N batches (reduces I/O during large scans).
     */
    default int getScanCheckpointInterval() {
        return 5;
    }

    /**
     * Whether embedding and summary caches are enabled.
     */
    default boolean isCacheEnabled() {
        return true;
    }

    /**
     * Maximum in-memory cache entries kept hot during a run.
     */
    default int getCacheHotSize() {
        return 512;
    }

    Optional<String> getProperty(String key);
}
