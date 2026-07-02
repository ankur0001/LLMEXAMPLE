package com.projectmind.adapter.vector;

import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.VectorDocument;
import com.projectmind.core.port.OllamaClientPort;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InMemoryVectorIndexTest {

    @Test
    void ranksDocumentsByCosineSimilarity() {
        OllamaClientPort ollama = mock(OllamaClientPort.class);
        when(ollama.embed(anyList())).thenReturn(
                List.of(new float[] {1f, 0f}, new float[] {0f, 1f}),
                List.of(new float[] {0.9f, 0.1f}));

        InMemoryVectorIndex index = VectorIndexTestSupport.createIndex(ollama);
        Path repo = Path.of("/tmp/repo");
        index.index(repo, List.of(
                new VectorDocument("a#0", "A.java", "user service repository", "", FileType.JAVA),
                new VectorDocument("b#0", "B.java", "docker kubernetes yaml", "", FileType.YAML)));

        var results = index.search(repo, "user service", 1, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).relativePath()).isEqualTo("A.java");
        assertThat(results.get(0).score()).isGreaterThan(0.5);
    }

    @Test
    void filtersByFileType() {
        OllamaClientPort ollama = mock(OllamaClientPort.class);
        when(ollama.embed(anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            if (texts.size() == 2) {
                return List.of(new float[] {1f, 0f}, new float[] {0f, 1f});
            }
            return switch (texts.get(0)) {
                case "alpha" -> List.of(new float[] {1f, 0f});
                case "beta" -> List.of(new float[] {0f, 1f});
                default -> List.of(new float[] {0f, 0f});
            };
        });

        InMemoryVectorIndex index = VectorIndexTestSupport.createIndex(ollama);
        Path repo = Path.of("/tmp/repo");
        index.index(repo, List.of(
                new VectorDocument("a#0", "A.java", "alpha", "", FileType.JAVA),
                new VectorDocument("b#0", "B.yml", "beta", "", FileType.YAML)));

        assertThat(index.search(repo, "alpha", 5, FileType.JAVA)).hasSize(1);
        assertThat(index.search(repo, "alpha", 5, FileType.JAVA).get(0).relativePath()).isEqualTo("A.java");
        assertThat(index.search(repo, "alpha", 5, FileType.YAML).get(0).score()).isLessThan(0.1);
        assertThat(index.search(repo, "beta", 5, FileType.YAML)).hasSize(1);
        assertThat(index.search(repo, "beta", 5, FileType.YAML).get(0).relativePath()).isEqualTo("B.yml");
        assertThat(index.search(repo, "beta", 5, FileType.JAVA).get(0).score()).isLessThan(0.1);
    }
}
