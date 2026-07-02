package com.projectmind.adapter.config;

import com.projectmind.core.port.ConfigurationPort;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Optional;

/**
 * Spring Boot configuration properties for ProjectMind.
 */
@ConfigurationProperties(prefix = "projectmind")
public class ProjectMindProperties implements ConfigurationPort {

    private Ollama ollama = new Ollama();
    private Vector vector = new Vector();
    private Memory memory = new Memory();
    private Scan scan = new Scan();
    private Docs docs = new Docs();
    private Plugins plugins = new Plugins();
    private Performance performance = new Performance();

    @Override
    public String getOllamaBaseUrl() {
        return ollama.getBaseUrl();
    }

    @Override
    public String getOllamaModel() {
        return ollama.getModel();
    }

    @Override
    public int getOllamaTimeoutSeconds() {
        return ollama.getTimeoutSeconds();
    }

    @Override
    public int getOllamaMaxRetries() {
        return ollama.getMaxRetries();
    }

    @Override
    public String getOllamaEmbedModel() {
        return ollama.getEmbedModel();
    }

    @Override
    public String getChromaUrl() {
        return vector.getChromaUrl();
    }

    @Override
    public int getVectorChunkSize() {
        return vector.getChunkSize();
    }

    @Override
    public int getVectorChunkOverlap() {
        return vector.getChunkOverlap();
    }

    @Override
    public int getVectorBatchSize() {
        return vector.getBatchSize();
    }

    @Override
    public String getVectorBackend() {
        return vector.getBackend();
    }

    @Override
    public String getGlobalDbPath() {
        return memory.getGlobalDb();
    }

    @Override
    public int getScanBatchSize() {
        return scan.getBatchSize();
    }

    @Override
    public List<String> getSkipDirectories() {
        return scan.getSkipDirs();
    }

    @Override
    public String getDocsOutputDir() {
        return docs.getOutputDir();
    }

    @Override
    public int getScanHashConcurrency() {
        return performance.getScanHashConcurrency();
    }

    @Override
    public int getParseConcurrency() {
        return performance.getParseConcurrency();
    }

    @Override
    public int getOllamaEmbedConcurrency() {
        return performance.getOllamaEmbedConcurrency();
    }

    @Override
    public int getSummaryConcurrency() {
        return performance.getSummaryConcurrency();
    }

    @Override
    public int getScanCheckpointInterval() {
        return performance.getScanCheckpointInterval();
    }

    @Override
    public boolean isCacheEnabled() {
        return memory.isCacheEnabled();
    }

    @Override
    public int getCacheHotSize() {
        return memory.getCacheHotSize();
    }

    @Override
    public Optional<String> getProperty(String key) {
        return Optional.empty();
    }

    public Ollama getOllama() { return ollama; }
    public void setOllama(Ollama ollama) { this.ollama = ollama; }
    public Vector getVector() { return vector; }
    public void setVector(Vector vector) { this.vector = vector; }
    public Memory getMemory() { return memory; }
    public void setMemory(Memory memory) { this.memory = memory; }
    public Scan getScan() { return scan; }
    public void setScan(Scan scan) { this.scan = scan; }
    public Docs getDocs() { return docs; }
    public void setDocs(Docs docs) { this.docs = docs; }
    public Plugins getPlugins() { return plugins; }
    public void setPlugins(Plugins plugins) { this.plugins = plugins; }
    public Performance getPerformance() { return performance; }
    public void setPerformance(Performance performance) { this.performance = performance; }

    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        private String model = "auto";
        private String embedModel = "auto";
        private int timeoutSeconds = 120;
        private int maxRetries = 2;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getEmbedModel() { return embedModel; }
        public void setEmbedModel(String embedModel) { this.embedModel = embedModel; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    }

    public static class Vector {
        private String chromaUrl = "http://localhost:8000";
        private String backend = "auto";
        private int chunkSize = 1500;
        private int chunkOverlap = 200;
        private int batchSize = 32;

        public String getChromaUrl() { return chromaUrl; }
        public void setChromaUrl(String chromaUrl) { this.chromaUrl = chromaUrl; }
        public String getBackend() { return backend; }
        public void setBackend(String backend) { this.backend = backend; }
        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
        public int getChunkOverlap() { return chunkOverlap; }
        public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }

    public static class Memory {
        private String globalDb = "~/.projectmind/projectmind.db";
        private boolean cacheEnabled = true;
        private int cacheHotSize = 512;

        public String getGlobalDb() { return globalDb; }
        public void setGlobalDb(String globalDb) { this.globalDb = globalDb; }
        public boolean isCacheEnabled() { return cacheEnabled; }
        public void setCacheEnabled(boolean cacheEnabled) { this.cacheEnabled = cacheEnabled; }
        public int getCacheHotSize() { return cacheHotSize; }
        public void setCacheHotSize(int cacheHotSize) { this.cacheHotSize = cacheHotSize; }
    }

    public static class Scan {
        private int batchSize = 100;
        private List<String> skipDirs = List.of(".git", "target", "build", "node_modules", "dist", "out", ".ai-memory");

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public List<String> getSkipDirs() { return skipDirs; }
        public void setSkipDirs(List<String> skipDirs) { this.skipDirs = skipDirs; }
    }

    public static class Docs {
        private String outputDir = "documentation";

        public String getOutputDir() { return outputDir; }
        public void setOutputDir(String outputDir) { this.outputDir = outputDir; }
    }

    public static class Plugins {
        private boolean enabled = true;
        private boolean autoDetect = true;
        private List<String> include = List.of();
        private List<String> exclude = List.of();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isAutoDetect() { return autoDetect; }
        public void setAutoDetect(boolean autoDetect) { this.autoDetect = autoDetect; }
        public List<String> getInclude() { return include; }
        public void setInclude(List<String> include) { this.include = include; }
        public List<String> getExclude() { return exclude; }
        public void setExclude(List<String> exclude) { this.exclude = exclude; }
    }

    public static class Performance {
        private int scanHashConcurrency = 0;
        private int parseConcurrency = 0;
        private int ollamaEmbedConcurrency = 4;
        private int summaryConcurrency = 4;
        private int scanCheckpointInterval = 5;

        public int getScanHashConcurrency() {
            return scanHashConcurrency <= 0
                    ? Math.max(2, Runtime.getRuntime().availableProcessors())
                    : scanHashConcurrency;
        }
        public void setScanHashConcurrency(int scanHashConcurrency) { this.scanHashConcurrency = scanHashConcurrency; }
        public int getParseConcurrency() {
            return parseConcurrency <= 0
                    ? Math.max(2, Runtime.getRuntime().availableProcessors())
                    : parseConcurrency;
        }
        public void setParseConcurrency(int parseConcurrency) { this.parseConcurrency = parseConcurrency; }
        public int getOllamaEmbedConcurrency() { return ollamaEmbedConcurrency; }
        public void setOllamaEmbedConcurrency(int ollamaEmbedConcurrency) { this.ollamaEmbedConcurrency = ollamaEmbedConcurrency; }
        public int getSummaryConcurrency() { return summaryConcurrency; }
        public void setSummaryConcurrency(int summaryConcurrency) { this.summaryConcurrency = summaryConcurrency; }
        public int getScanCheckpointInterval() { return scanCheckpointInterval; }
        public void setScanCheckpointInterval(int scanCheckpointInterval) { this.scanCheckpointInterval = scanCheckpointInterval; }
    }
}
