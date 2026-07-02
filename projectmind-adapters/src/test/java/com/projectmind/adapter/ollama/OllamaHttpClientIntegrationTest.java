package com.projectmind.adapter.ollama;

import com.projectmind.adapter.config.ProjectMindProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OllamaHttpClientIntegrationTest {

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void completesAgainstLocalOllamaWhenAvailable() {
        ProjectMindProperties config = new ProjectMindProperties();
        config.getOllama().setTimeoutSeconds(60);
        OllamaHttpClient client = new OllamaHttpClient(config);
        assumeTrue(client.isAvailable(), "Local Ollama with a completion model is not available");

        String response = client.complete("Reply with exactly one word: ready");

        assertThat(response).isNotBlank();
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void embedsAgainstLocalOllamaWhenAvailable() {
        ProjectMindProperties config = new ProjectMindProperties();
        config.getOllama().setTimeoutSeconds(45);
        OllamaHttpClient client = new OllamaHttpClient(config);
        assumeTrue(client.isEmbeddingAvailable(), "Local Ollama with an embedding model is not available");

        var embeddings = client.embed(java.util.List.of("ProjectMind repository memory"));

        assertThat(embeddings).hasSize(1);
        assertThat(embeddings.get(0).length).isGreaterThan(0);
    }
}
