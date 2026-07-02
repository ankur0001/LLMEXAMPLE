package com.projectmind.adapter.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.projectmind.core.path.RepositoryPathResolver;
import com.projectmind.core.domain.ApiSummary;
import com.projectmind.core.domain.ClassSummary;
import com.projectmind.core.domain.DocSection;
import com.projectmind.core.domain.FileSummary;
import com.projectmind.core.domain.HistorySnapshot;
import com.projectmind.core.domain.KnowledgeGraph;
import com.projectmind.core.domain.MemoryOverview;
import com.projectmind.core.domain.PackageSummary;
import com.projectmind.core.domain.ProjectMetadata;
import com.projectmind.core.domain.RepositoryIndex;
import com.projectmind.core.domain.ScanCheckpoint;
import com.projectmind.core.port.ConfigurationPort;
import com.projectmind.core.port.MemoryManagerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Manages persistent project memory in {@code .ai-memory/} with JSON artifacts and SQLite indexing.
 */
@Component
public class JsonMemoryManager implements MemoryManagerPort {

    private static final Logger log = LoggerFactory.getLogger(JsonMemoryManager.class);

    private static final String PROJECT_JSON = "project.json";
    private static final String REPOSITORY_INDEX_JSON = "repository_index.json";
    private static final String DEPENDENCY_GRAPH_JSON = "dependency_graph.json";
    private static final String DOC_SECTIONS_JSON = "documentation/sections.json";
    private static final String SCAN_PROGRESS_JSON = MemoryManagerPort.SCAN_PROGRESS_JSON;
    private static final String SCAN_CHECKPOINT_JSON = MemoryManagerPort.SCAN_CHECKPOINT_JSON;

    private static final String SUMMARY_TYPE_FILE = "file";
    private static final String SUMMARY_TYPE_PACKAGE = "package";
    private static final String SUMMARY_TYPE_CLASS = "class";
    private static final String SUMMARY_TYPE_API = "api";

    private final ObjectMapper objectMapper;
    private final SqliteMetadataStore sqlite;
    private final ConfigurationPort configuration;
    private final java.util.concurrent.ConcurrentHashMap<String, String> hotCache = new java.util.concurrent.ConcurrentHashMap<>();

    public JsonMemoryManager(ConfigurationPort configuration) {
        this.configuration = configuration;
        this.objectMapper = createObjectMapper();
        this.sqlite = new SqliteMetadataStore(configuration.getGlobalDbPath());
    }

    /**
     * Convenience constructor for tests with an isolated SQLite database.
     */
    public JsonMemoryManager() {
        this(createTestConfiguration());
    }

    @Override
    public Path initializeMemory(Path repositoryPath) {
        Path memoryDir = memoryPath(repositoryPath);
        try {
            Files.createDirectories(memoryDir);
            for (String subdir : new String[]{
                    "embeddings", "summaries", "package_summaries", "class_summaries",
                    "api_summaries", "diagrams", "documentation", "history", "cache"}) {
                Files.createDirectories(memoryDir.resolve(subdir));
            }
            log.info("Initialized memory at: {}", memoryDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize memory directory", e);
        }
        return memoryDir;
    }

    @Override
    public Optional<ProjectMetadata> loadMetadata(Path repositoryPath) {
        return readJson(memoryPath(repositoryPath).resolve(PROJECT_JSON), ProjectMetadata.class);
    }

    @Override
    public void saveMetadata(Path repositoryPath, ProjectMetadata metadata) {
        writeJson(memoryPath(repositoryPath).resolve(PROJECT_JSON), metadata);
        sqlite.upsertProject(normalizeRepositoryPath(repositoryPath), metadata);
    }

    @Override
    public Optional<RepositoryIndex> loadIndex(Path repositoryPath) {
        return readJson(memoryPath(repositoryPath).resolve(REPOSITORY_INDEX_JSON), RepositoryIndex.class);
    }

    @Override
    public void saveIndex(Path repositoryPath, RepositoryIndex index) {
        writeJson(memoryPath(repositoryPath).resolve(REPOSITORY_INDEX_JSON), index);
    }

    @Override
    public Optional<RepositoryIndex> loadScanProgress(Path repositoryPath) {
        return readJson(memoryPath(repositoryPath).resolve(SCAN_PROGRESS_JSON), RepositoryIndex.class);
    }

    @Override
    public void saveScanProgress(Path repositoryPath, RepositoryIndex progress) {
        writeJson(memoryPath(repositoryPath).resolve(SCAN_PROGRESS_JSON), progress);
    }

    @Override
    public void clearScanProgress(Path repositoryPath) {
        deleteIfExists(memoryPath(repositoryPath).resolve(SCAN_PROGRESS_JSON));
    }

    @Override
    public Optional<ScanCheckpoint> loadScanCheckpoint(Path repositoryPath) {
        return readJson(memoryPath(repositoryPath).resolve(SCAN_CHECKPOINT_JSON), ScanCheckpoint.class);
    }

    @Override
    public void saveScanCheckpoint(Path repositoryPath, ScanCheckpoint checkpoint) {
        writeJson(memoryPath(repositoryPath).resolve(SCAN_CHECKPOINT_JSON), checkpoint);
    }

    @Override
    public void clearScanCheckpoint(Path repositoryPath) {
        deleteIfExists(memoryPath(repositoryPath).resolve(SCAN_CHECKPOINT_JSON));
    }

    @Override
    public Optional<KnowledgeGraph> loadGraph(Path repositoryPath) {
        return readJson(memoryPath(repositoryPath).resolve(DEPENDENCY_GRAPH_JSON), KnowledgeGraph.class);
    }

    @Override
    public void saveGraph(Path repositoryPath, KnowledgeGraph graph) {
        writeJson(memoryPath(repositoryPath).resolve(DEPENDENCY_GRAPH_JSON), graph);
        sqlite.syncGraph(normalizeRepositoryPath(repositoryPath), graph);
    }

    @Override
    public void saveSummary(Path repositoryPath, FileSummary summary) {
        Path path = summaryPath(repositoryPath, "summaries", summary.relativePath());
        writeJson(path, summary);
        sqlite.upsertSummary(
                normalizeRepositoryPath(repositoryPath),
                SUMMARY_TYPE_FILE,
                summary.relativePath(),
                writeJsonString(summary),
                Instant.now());
    }

    @Override
    public Optional<FileSummary> loadSummary(Path repositoryPath, String relativePath) {
        return readJson(summaryPath(repositoryPath, "summaries", relativePath), FileSummary.class);
    }

    @Override
    public void deleteSummary(Path repositoryPath, String relativePath) {
        Path path = summaryPath(repositoryPath, "summaries", relativePath);
        deleteIfExists(path);
        sqlite.deleteSummary(normalizeRepositoryPath(repositoryPath), SUMMARY_TYPE_FILE, relativePath);
    }

    @Override
    public void saveDocumentationSections(Path repositoryPath, List<DocSection> sections) {
        writeJson(memoryPath(repositoryPath).resolve(DOC_SECTIONS_JSON), sections);
    }

    @Override
    public Optional<List<DocSection>> loadDocumentationSections(Path repositoryPath) {
        return readJson(
                memoryPath(repositoryPath).resolve(DOC_SECTIONS_JSON),
                new TypeReference<List<DocSection>>() { });
    }

    @Override
    public void savePackageSummary(Path repositoryPath, PackageSummary summary) {
        Path path = summaryPath(repositoryPath, "package_summaries", summary.packageName());
        writeJson(path, summary);
        sqlite.upsertSummary(
                normalizeRepositoryPath(repositoryPath),
                SUMMARY_TYPE_PACKAGE,
                summary.packageName(),
                writeJsonString(summary),
                Instant.now());
    }

    @Override
    public Optional<PackageSummary> loadPackageSummary(Path repositoryPath, String packageName) {
        return readJson(summaryPath(repositoryPath, "package_summaries", packageName), PackageSummary.class);
    }

    @Override
    public void saveClassSummary(Path repositoryPath, ClassSummary summary) {
        Path path = summaryPath(repositoryPath, "class_summaries", summary.qualifiedName());
        writeJson(path, summary);
        sqlite.upsertSummary(
                normalizeRepositoryPath(repositoryPath),
                SUMMARY_TYPE_CLASS,
                summary.qualifiedName(),
                writeJsonString(summary),
                Instant.now());
    }

    @Override
    public Optional<ClassSummary> loadClassSummary(Path repositoryPath, String qualifiedName) {
        return readJson(summaryPath(repositoryPath, "class_summaries", qualifiedName), ClassSummary.class);
    }

    @Override
    public void saveApiSummary(Path repositoryPath, ApiSummary summary) {
        String key = apiKey(summary);
        Path path = summaryPath(repositoryPath, "api_summaries", key);
        writeJson(path, summary);
        sqlite.upsertSummary(
                normalizeRepositoryPath(repositoryPath),
                SUMMARY_TYPE_API,
                key,
                writeJsonString(summary),
                Instant.now());
    }

    @Override
    public Optional<ApiSummary> loadApiSummary(Path repositoryPath, String endpointKey) {
        return readJson(summaryPath(repositoryPath, "api_summaries", endpointKey), ApiSummary.class);
    }

    @Override
    public void saveHistorySnapshot(Path repositoryPath, HistorySnapshot snapshot) {
        String fileName = MemoryPaths.historyFileName(snapshot.timestamp().toEpochMilli(), snapshot.operation());
        writeJson(memoryPath(repositoryPath).resolve("history").resolve(fileName), snapshot);
        sqlite.addHistoryEntry(normalizeRepositoryPath(repositoryPath), snapshot);
    }

    @Override
    public List<HistorySnapshot> listHistorySnapshots(Path repositoryPath, int limit) {
        List<HistorySnapshot> fromSql = sqlite.listHistory(normalizeRepositoryPath(repositoryPath), limit);
        if (!fromSql.isEmpty()) {
            return fromSql;
        }
        Path historyDir = memoryPath(repositoryPath).resolve("history");
        if (!Files.isDirectory(historyDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(historyDir)) {
            return files
                    .filter(path -> path.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .limit(Math.max(1, limit))
                    .map(path -> readJson(path, HistorySnapshot.class).orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            log.warn("Failed to list history snapshots: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public void putCacheEntry(Path repositoryPath, String key, String value) {
        if (!configuration.isCacheEnabled()) {
            return;
        }
        String hotKey = hotCacheKey(repositoryPath, key);
        putHotCache(hotKey, value);
        Path cacheFile = memoryPath(repositoryPath).resolve("cache").resolve(MemoryPaths.cacheFileName(key));
        writeJson(cacheFile, Map.of(
                "key", key,
                "value", value,
                "storedAt", Instant.now().toString()));
    }

    @Override
    public Optional<String> getCacheEntry(Path repositoryPath, String key) {
        if (!configuration.isCacheEnabled()) {
            return Optional.empty();
        }
        String hotKey = hotCacheKey(repositoryPath, key);
        String cached = hotCache.get(hotKey);
        if (cached != null) {
            return Optional.of(cached);
        }
        Path cacheFile = memoryPath(repositoryPath).resolve("cache").resolve(MemoryPaths.cacheFileName(key));
        Optional<String> value = readJson(cacheFile, Map.class)
                .map(raw -> String.valueOf(raw.get("value")));
        value.ifPresent(found -> putHotCache(hotKey, found));
        return value;
    }

    @Override
    public void clearCache(Path repositoryPath) {
        hotCache.keySet().removeIf(entry -> entry.startsWith(repositoryPath.toAbsolutePath().normalize() + ":"));
        Path cacheDir = memoryPath(repositoryPath).resolve("cache");
        if (!Files.isDirectory(cacheDir)) {
            return;
        }
        try (Stream<Path> files = Files.list(cacheDir)) {
            for (Path file : files.toList()) {
                deleteIfExists(file);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to clear cache at " + cacheDir, e);
        }
    }

    @Override
    public Optional<MemoryOverview> loadOverview(Path repositoryPath) {
        Optional<ProjectMetadata> metadata = loadMetadata(repositoryPath);
        if (metadata.isEmpty()) {
            return Optional.empty();
        }

        String repoKey = normalizeRepositoryPath(repositoryPath);
        SqliteMetadataStore.GraphCounts graphCounts = sqlite.countGraph(repoKey);
        int graphNodes = graphCounts.nodeCount();
        int graphEdges = graphCounts.edgeCount();
        if (graphNodes == 0) {
            KnowledgeGraph graph = loadGraph(repositoryPath).orElse(new KnowledgeGraph(List.of(), List.of()));
            graphNodes = graph.nodes().size();
            graphEdges = graph.edges().size();
        }

        return Optional.of(new MemoryOverview(
                metadata.get(),
                graphNodes,
                graphEdges,
                countJsonFiles(repositoryPath, "summaries"),
                countJsonFiles(repositoryPath, "package_summaries"),
                countJsonFiles(repositoryPath, "class_summaries"),
                countJsonFiles(repositoryPath, "api_summaries"),
                sqlite.countHistory(repoKey),
                countJsonFiles(repositoryPath, "cache"),
                listHistorySnapshots(repositoryPath, 5)));
    }

    @Override
    public void clean(Path repositoryPath) {
        sqlite.deleteProject(normalizeRepositoryPath(repositoryPath));
        Path memoryDir = memoryPath(repositoryPath);
        if (Files.exists(memoryDir)) {
            try {
                deleteRecursively(memoryDir);
                log.info("Cleaned memory at: {}", memoryDir);
            } catch (IOException e) {
                throw new RuntimeException("Failed to clean memory directory", e);
            }
        }
    }

    @Override
    public Path memoryPath(Path repositoryPath) {
        return RepositoryPathResolver.resolve(repositoryPath).resolve(MEMORY_DIR);
    }

    private Path summaryPath(Path repositoryPath, String subdir, String key) {
        return memoryPath(repositoryPath).resolve(subdir).resolve(MemoryPaths.safeFileName(key) + ".json");
    }

    private static String apiKey(ApiSummary summary) {
        return summary.httpMethod().toUpperCase() + "_" + summary.path();
    }

    private int countJsonFiles(Path repositoryPath, String subdir) {
        Path dir = memoryPath(repositoryPath).resolve(subdir);
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(dir)) {
            return (int) files.filter(path -> path.toString().endsWith(".json")).count();
        } catch (IOException e) {
            return 0;
        }
    }

    private String writeJsonString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize JSON", e);
        }
    }

    private static String normalizeRepositoryPath(Path repositoryPath) {
        return RepositoryPathResolver.toStorageKey(repositoryPath);
    }

    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static ConfigurationPort createTestConfiguration() {
        String dbPath = Path.of(System.getProperty("java.io.tmpdir"),
                "projectmind-test-" + UUID.randomUUID() + ".db").toString();
        return new ConfigurationPort() {
            @Override public String getOllamaBaseUrl() { return "http://localhost:11434"; }
            @Override public String getOllamaModel() { return "test"; }
            @Override public String getChromaUrl() { return "http://localhost:8000"; }
            @Override public String getGlobalDbPath() { return dbPath; }
            @Override public int getScanBatchSize() { return 100; }
            @Override public List<String> getSkipDirectories() { return List.of(".git", "target"); }
            @Override public String getDocsOutputDir() { return "documentation"; }
            @Override public Optional<String> getProperty(String key) { return Optional.empty(); }
        };
    }

    private <T> Optional<T> readJson(Path path, Class<T> type) {
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(path.toFile(), type));
        } catch (IOException e) {
            log.warn("Failed to read {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    private <T> Optional<T> readJson(Path path, TypeReference<T> type) {
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(path.toFile(), type));
        } catch (IOException e) {
            log.warn("Failed to read {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    private void writeJson(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            objectMapper.writeValue(path.toFile(), value);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + path, e);
        }
    }

    private void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete {}: {}", path, e.getMessage());
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                for (Path child : stream.toList()) {
                    deleteRecursively(child);
                }
            }
        }
        Files.delete(path);
    }

    private String hotCacheKey(Path repositoryPath, String key) {
        return repositoryPath.toAbsolutePath().normalize() + ":" + key;
    }

    private void putHotCache(String hotKey, String value) {
        int maxSize = Math.max(64, configuration.getCacheHotSize());
        if (hotCache.size() >= maxSize) {
            hotCache.keySet().stream().findFirst().ifPresent(hotCache::remove);
        }
        hotCache.put(hotKey, value);
    }
}
