package com.projectmind.adapter.ollama;

import com.projectmind.core.port.ConfigurationPort;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaHttpClientTest {

    private MockWebServer server;
    private OllamaHttpClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = OllamaHttpClient.forTesting(testConfig(), WebClient.builder().baseUrl(server.url("/").toString()).build());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void completeReturnsModelResponse() {
        enqueueTags("""
                {"models":[{"name":"qwen2.5-coder:14b-instruct","capabilities":["completion"]}]}
                """);
        server.enqueue(new MockResponse()
                .setBody("{\"response\":\"Summary text\",\"done\":true}")
                .addHeader("Content-Type", "application/json"));

        String response = client.complete("Summarize this file");

        assertThat(response).isEqualTo("Summary text");
    }

    @Test
    void completeStreamingEmitsChunks() {
        enqueueTags("""
                {"models":[{"name":"qwen2.5-coder:14b-instruct","capabilities":["completion"]}]}
                """);
        server.enqueue(new MockResponse()
                .setBody("""
                        {"response":"Hel","done":false}
                        {"response":"lo","done":true}
                        """)
                .addHeader("Content-Type", "application/x-ndjson"));

        AtomicReference<String> streamed = new AtomicReference<>("");
        client.completeStreaming("Say hello", chunk -> streamed.updateAndGet(current -> current + chunk));

        assertThat(streamed.get()).isEqualTo("Hello");
    }

    @Test
    void embedReturnsVector() {
        enqueueTags("""
                {"models":[{"name":"nomic-embed-text:latest","capabilities":["embedding"]}]}
                """);
        server.enqueue(new MockResponse()
                .setBody("{\"embedding\":[0.1,0.2,0.3]}")
                .addHeader("Content-Type", "application/json"));

        List<float[]> embeddings = client.embed(List.of("hello world"));

        assertThat(embeddings).hasSize(1);
        assertThat(embeddings.get(0)).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void isAvailableWhenAnyCompletionModelInstalled() {
        enqueueTags("""
                {"models":[{"name":"phi3:latest","capabilities":["completion"]}]}
                """);

        assertThat(client.isAvailable()).isTrue();
    }

    @Test
    void completeUsesInstalledModelWhenPreferredMissing() {
        enqueueTags("""
                {"models":[{"name":"phi3:latest","capabilities":["completion"]}]}
                """);
        server.enqueue(new MockResponse()
                .setBody("{\"response\":\"Answer text\",\"done\":true}")
                .addHeader("Content-Type", "application/json"));

        String response = client.complete("test");

        assertThat(response).isEqualTo("Answer text");
        assertThat(client.getModelName()).isEqualTo("phi3:latest");
    }

    @Test
    void requireReadySucceedsWhenAnyCompletionModelInstalled() {
        enqueueTags("""
                {"models":[{"name":"phi3:latest","capabilities":["completion"]},{"name":"nomic-embed-text:latest","capabilities":["embedding"]}]}
                """);

        client.requireReady();

        assertThat(client.getModelName()).isEqualTo("phi3:latest");
    }

    @Test
    void completeThrowsWhenResponseEmpty() throws IOException {
        MockWebServer local = new MockWebServer();
        local.start();
        try {
            local.enqueue(new MockResponse()
                    .setBody("""
                            {"models":[{"name":"qwen2.5-coder:14b-instruct","capabilities":["completion"]}]}
                            """)
                    .addHeader("Content-Type", "application/json"));
            local.enqueue(new MockResponse()
                    .setBody("{\"response\":null,\"done\":true}")
                    .addHeader("Content-Type", "application/json"));

            OllamaHttpClient isolated = OllamaHttpClient.forTesting(
                    testConfig(),
                    WebClient.builder().baseUrl(local.url("/").toString()).build());

            assertThatThrownBy(() -> isolated.complete("test"))
                    .isInstanceOf(OllamaClientException.class)
                    .hasMessageContaining("empty completion");
        } finally {
            local.shutdown();
        }
    }

    private void enqueueTags(String body) {
        server.enqueue(new MockResponse()
                .setBody(body)
                .addHeader("Content-Type", "application/json"));
    }

    private static ConfigurationPort testConfig() {
        return new ConfigurationPort() {
            @Override public String getOllamaBaseUrl() { return "http://localhost"; }
            @Override public String getOllamaModel() { return "qwen2.5-coder:14b-instruct"; }
            @Override public int getOllamaTimeoutSeconds() { return 5; }
            @Override public int getOllamaMaxRetries() { return 0; }
            @Override public String getOllamaEmbedModel() { return "nomic-embed-text"; }
            @Override public String getChromaUrl() { return "http://localhost:8000"; }
            @Override public String getGlobalDbPath() { return ":memory:"; }
            @Override public int getScanBatchSize() { return 100; }
            @Override public List<String> getSkipDirectories() { return List.of(".git"); }
            @Override public String getDocsOutputDir() { return "documentation"; }
            @Override public Optional<String> getProperty(String key) { return Optional.empty(); }
        };
    }
}
