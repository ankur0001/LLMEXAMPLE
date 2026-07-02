package com.projectmind.adapter.vector;

import com.projectmind.core.port.OllamaClientPort;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChromaVectorIndexTest {

    private MockWebServer server;
    private OllamaClientPort ollama;
    private ChromaVectorIndex index;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        ollama = mock(OllamaClientPort.class);
        index = new ChromaVectorIndex(
                WebClient.builder().baseUrl(server.url("/").toString()).build(),
                ollama,
                org.mockito.Mockito.mock(com.projectmind.core.port.MemoryManagerPort.class),
                org.mockito.Mockito.mock(com.projectmind.core.port.ConfigurationPort.class, invocation -> {
                    if ("isCacheEnabled".equals(invocation.getMethod().getName())) {
                        return false;
                    }
                    return org.mockito.Mockito.RETURNS_DEFAULTS.answer(invocation);
                }));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void indexesAndQueriesDocuments() {
        when(ollama.embed(anyList())).thenReturn(
                List.of(new float[] {0.1f, 0.2f}),
                List.of(new float[] {0.1f, 0.2f}));

        server.enqueue(new MockResponse()
                .setBody("{\"id\":\"col-1\",\"name\":\"pm_test\"}")
                .addHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse().setResponseCode(200));
        server.enqueue(new MockResponse()
                .setBody("""
                        {
                          "ids":[["doc-1"]],
                          "documents":[["service layer"]],
                          "distances":[[0.1]],
                          "metadatas":[[{"relative_path":"Service.java","file_type":"JAVA"}]]
                        }
                        """)
                .addHeader("Content-Type", "application/json"));

        Path repo = Path.of("/tmp/chroma-repo");
        index.index(repo, List.of(new com.projectmind.core.domain.VectorDocument(
                "doc-1", "Service.java", "service layer", "", com.projectmind.core.domain.FileType.JAVA)));

        var results = index.search(repo, "service", 1, null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).relativePath()).isEqualTo("Service.java");
    }
}
