package com.projectmind.adapter.vector;

import com.projectmind.adapter.config.ProjectMindProperties;
import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.SearchResult;
import com.projectmind.core.domain.VectorDocument;
import com.projectmind.core.port.ConfigurationPort;
import com.projectmind.core.port.MemoryManagerPort;
import com.projectmind.core.port.OllamaClientPort;
import com.projectmind.core.port.VectorIndexPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.projectmind.adapter.vector.ChromaApiModels.AddRequest;
import static com.projectmind.adapter.vector.ChromaApiModels.CreateCollectionRequest;
import static com.projectmind.adapter.vector.ChromaApiModels.CreateCollectionResponse;
import static com.projectmind.adapter.vector.ChromaApiModels.QueryRequest;
import static com.projectmind.adapter.vector.ChromaApiModels.QueryResponse;

/**
 * ChromaDB-backed vector index using Ollama-generated embeddings.
 */
public class ChromaVectorIndex implements VectorIndexPort {

    private static final Logger log = LoggerFactory.getLogger(ChromaVectorIndex.class);

    private final WebClient webClient;
    private final OllamaClientPort ollamaClient;
    private final MemoryManagerPort memoryManager;
    private final ConfigurationPort configuration;
    private final Map<String, String> collectionIds = new ConcurrentHashMap<>();
    private final Map<String, Long> documentCounts = new ConcurrentHashMap<>();

    public ChromaVectorIndex(
            ProjectMindProperties config,
            OllamaClientPort ollamaClient,
            MemoryManagerPort memoryManager) {
        this(WebClient.builder().baseUrl(config.getChromaUrl()).build(), ollamaClient, memoryManager, config);
    }

    ChromaVectorIndex(
            WebClient webClient,
            OllamaClientPort ollamaClient,
            MemoryManagerPort memoryManager,
            ConfigurationPort configuration) {
        this.webClient = webClient;
        this.ollamaClient = ollamaClient;
        this.memoryManager = memoryManager;
        this.configuration = configuration;
    }

    static boolean isAvailable(String chromaUrl) {
        try {
            WebClient client = WebClient.builder().baseUrl(chromaUrl).build();
            client.get().uri("/api/v1/heartbeat").retrieve().bodyToMono(String.class).block();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public void index(Path repositoryPath, List<VectorDocument> documents) {
        if (documents.isEmpty()) {
            return;
        }
        String collectionId = ensureCollection(repositoryPath);
        List<String> texts = documents.stream().map(VectorDocument::content).toList();
        List<float[]> embeddings = EmbeddingCache.embedWithCache(
                repositoryPath, texts, ollamaClient, memoryManager, configuration);

        List<String> ids = new ArrayList<>();
        List<Map<String, Object>> metadatas = new ArrayList<>();
        List<List<Float>> embeddingPayload = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            VectorDocument document = documents.get(i);
            ids.add(document.id());
            metadatas.add(Map.of(
                    "relative_path", document.relativePath(),
                    "file_type", document.fileType().name()));
            embeddingPayload.add(toFloatList(embeddings.get(i)));
        }

        webClient.post()
                .uri("/api/v1/collections/{collectionId}/add", collectionId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AddRequest(ids, texts, embeddingPayload, metadatas))
                .retrieve()
                .toBodilessEntity()
                .block();

        documentCounts.merge(repositoryKey(repositoryPath), (long) documents.size(), Long::sum);
        log.debug("Indexed {} documents in Chroma collection {} for {}", documents.size(), collectionId, repositoryPath);
    }

    @Override
    public List<SearchResult> search(Path repositoryPath, String query, int topK, FileType fileTypeFilter) {
        String collectionId = collectionIds.get(repositoryKey(repositoryPath));
        if (collectionId == null) {
            return List.of();
        }

        float[] queryEmbedding = EmbeddingCache.embedWithCache(
                repositoryPath, List.of(query), ollamaClient, memoryManager, configuration).get(0);
        Map<String, Object> where = fileTypeFilter != null
                ? Map.of("file_type", Map.of("$eq", fileTypeFilter.name()))
                : Map.of();

        QueryResponse response = webClient.post()
                .uri("/api/v1/collections/{collectionId}/query", collectionId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new QueryRequest(List.of(toFloatList(queryEmbedding)), Math.max(1, topK), where))
                .retrieve()
                .bodyToMono(QueryResponse.class)
                .block();

        if (response == null || response.ids() == null || response.ids().isEmpty()) {
            return List.of();
        }

        List<SearchResult> results = new ArrayList<>();
        List<String> ids = response.ids().get(0);
        List<String> documents = response.documents().get(0);
        List<Double> distances = response.distances() != null ? response.distances().get(0) : List.of();
        List<Map<String, Object>> metadatas = response.metadatas() != null ? response.metadatas().get(0) : List.of();

        for (int i = 0; i < ids.size(); i++) {
            String relativePath = metadatas.size() > i
                    ? String.valueOf(metadatas.get(i).getOrDefault("relative_path", ""))
                    : "";
            FileType fileType = metadatas.size() > i
                    ? FileType.valueOf(String.valueOf(metadatas.get(i).getOrDefault("file_type", FileType.OTHER.name())))
                    : FileType.OTHER;
            String snippet = documents.size() > i
                    ? documents.get(i)
                    : "";
            double score = distances.size() > i ? 1.0 / (1.0 + distances.get(i)) : 0.0;
            results.add(new SearchResult(relativePath, truncate(snippet), score, fileType));
        }
        return results;
    }

    @Override
    public void remove(Path repositoryPath, List<String> relativePaths) {
        String collectionId = collectionIds.get(repositoryKey(repositoryPath));
        if (collectionId == null || relativePaths.isEmpty()) {
            return;
        }
        webClient.post()
                .uri("/api/v1/collections/{collectionId}/delete", collectionId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("where", Map.of("relative_path", Map.of("$in", relativePaths))))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    @Override
    public long count(Path repositoryPath) {
        return documentCounts.getOrDefault(repositoryKey(repositoryPath), 0L);
    }

    private String ensureCollection(Path repositoryPath) {
        return collectionIds.computeIfAbsent(repositoryKey(repositoryPath), key -> {
            String collectionName = collectionName(repositoryPath);
            CreateCollectionResponse response = webClient.post()
                    .uri("/api/v1/collections")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new CreateCollectionRequest(collectionName, true))
                    .retrieve()
                    .bodyToMono(CreateCollectionResponse.class)
                    .block();
            if (response == null || response.id() == null) {
                throw new VectorIndexException("Failed to create Chroma collection for " + repositoryPath);
            }
            return response.id();
        });
    }

    private static String collectionName(Path repositoryPath) {
        return "pm_" + sha256(repositoryKey(repositoryPath)).substring(0, 16);
    }

    private static String repositoryKey(Path repositoryPath) {
        return repositoryPath.toAbsolutePath().normalize().toString();
    }

    private static List<Float> toFloatList(float[] values) {
        List<Float> floats = new ArrayList<>(values.length);
        for (float value : values) {
            floats.add(value);
        }
        return floats;
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new VectorIndexException("Failed to hash repository path", ex);
        }
    }
}
