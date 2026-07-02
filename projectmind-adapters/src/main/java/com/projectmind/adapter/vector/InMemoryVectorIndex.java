package com.projectmind.adapter.vector;

import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.SearchResult;
import com.projectmind.core.domain.VectorDocument;
import com.projectmind.adapter.config.ProjectMindProperties;
import com.projectmind.core.port.ConfigurationPort;
import com.projectmind.core.port.MemoryManagerPort;
import com.projectmind.core.port.OllamaClientPort;
import com.projectmind.core.port.VectorIndexPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory vector index with Ollama embeddings and cosine similarity search.
 */
public class InMemoryVectorIndex implements VectorIndexPort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryVectorIndex.class);

    private record StoredDocument(VectorDocument document, float[] embedding) {
    }

    private final OllamaClientPort ollamaClient;
    private final MemoryManagerPort memoryManager;
    private final ConfigurationPort configuration;
    private final Map<String, List<StoredDocument>> store = new ConcurrentHashMap<>();

    public InMemoryVectorIndex(
            OllamaClientPort ollamaClient,
            MemoryManagerPort memoryManager,
            ConfigurationPort configuration) {
        this.ollamaClient = ollamaClient;
        this.memoryManager = memoryManager;
        this.configuration = configuration;
    }

    @Override
    public void index(Path repositoryPath, List<VectorDocument> documents) {
        if (documents.isEmpty()) {
            return;
        }
        String key = repositoryKey(repositoryPath);
        List<String> texts = documents.stream().map(VectorDocument::content).toList();
        List<float[]> embeddings = EmbeddingCache.embedWithCache(
                repositoryPath, texts, ollamaClient, memoryManager, configuration);

        List<StoredDocument> stored = new ArrayList<>(documents.size());
        for (int i = 0; i < documents.size(); i++) {
            stored.add(new StoredDocument(documents.get(i), embeddings.get(i)));
        }
        store.computeIfAbsent(key, ignored -> new ArrayList<>()).addAll(stored);
        log.debug("Indexed {} documents in memory for {}", documents.size(), repositoryPath);
    }

    @Override
    public List<SearchResult> search(Path repositoryPath, String query, int topK, FileType fileTypeFilter) {
        List<StoredDocument> docs = store.getOrDefault(repositoryKey(repositoryPath), List.of());
        if (docs.isEmpty()) {
            return List.of();
        }

        float[] queryEmbedding = EmbeddingCache.embedWithCache(
                repositoryPath, List.of(query), ollamaClient, memoryManager, configuration).get(0);
        return docs.stream()
                .filter(doc -> fileTypeFilter == null || doc.document().fileType() == fileTypeFilter)
                .map(doc -> new RankedResult(doc, VectorMath.cosineSimilarity(queryEmbedding, doc.embedding())))
                .sorted(Comparator.comparingDouble(RankedResult::score).reversed())
                .limit(Math.max(1, topK))
                .map(ranked -> toSearchResult(ranked.document(), ranked.score()))
                .toList();
    }

    @Override
    public void remove(Path repositoryPath, List<String> relativePaths) {
        List<StoredDocument> docs = store.get(repositoryKey(repositoryPath));
        if (docs != null) {
            docs.removeIf(doc -> relativePaths.contains(doc.document().relativePath()));
        }
    }

    @Override
    public long count(Path repositoryPath) {
        return store.getOrDefault(repositoryKey(repositoryPath), List.of()).size();
    }

    private static SearchResult toSearchResult(StoredDocument stored, double score) {
        VectorDocument doc = stored.document();
        String snippet = doc.summary() != null && !doc.summary().isBlank()
                ? doc.summary()
                : doc.content().substring(0, Math.min(200, doc.content().length()));
        return new SearchResult(doc.relativePath(), snippet, score, doc.fileType());
    }

    private static String repositoryKey(Path repositoryPath) {
        return repositoryPath.toAbsolutePath().normalize().toString();
    }

    private record RankedResult(StoredDocument document, double score) {
    }
}
