package com.projectmind.adapter.vector;

import com.projectmind.core.port.ConfigurationPort;
import com.projectmind.core.port.MemoryManagerPort;
import com.projectmind.core.port.OllamaClientPort;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Caches Ollama embedding vectors keyed by content hash in {@code .ai-memory/cache/}.
 */
final class EmbeddingCache {

    private static final String KEY_PREFIX = "embed:";

    private EmbeddingCache() {
    }

    static List<float[]> embedWithCache(
            Path repositoryPath,
            List<String> texts,
            OllamaClientPort ollamaClient,
            MemoryManagerPort memoryManager,
            ConfigurationPort configuration) {
        if (texts.isEmpty()) {
            return List.of();
        }
        if (!configuration.isCacheEnabled()) {
            return ollamaClient.embed(texts);
        }

        List<float[]> results = new ArrayList<>(texts.size());
        List<Integer> missIndexes = new ArrayList<>();
        List<String> missTexts = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            String key = cacheKey(texts.get(i));
            var cached = memoryManager.getCacheEntry(repositoryPath, key);
            if (cached.isPresent()) {
                results.add(EmbeddingSerializer.deserialize(cached.get()));
            } else {
                results.add(null);
                missIndexes.add(i);
                missTexts.add(texts.get(i));
            }
        }

        if (!missTexts.isEmpty()) {
            List<float[]> fresh = ollamaClient.embed(missTexts);
            for (int j = 0; j < missIndexes.size(); j++) {
                int index = missIndexes.get(j);
                float[] embedding = fresh.get(j);
                results.set(index, embedding);
                memoryManager.putCacheEntry(
                        repositoryPath,
                        cacheKey(texts.get(index)),
                        EmbeddingSerializer.serialize(embedding));
            }
        }

        return List.copyOf(results);
    }

    private static String cacheKey(String text) {
        return KEY_PREFIX + sha256(text);
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
