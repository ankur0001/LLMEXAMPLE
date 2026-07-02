package com.projectmind.adapter.docs;

import com.projectmind.adapter.knowledge.DefaultKnowledgeGraphBuilder;
import com.projectmind.adapter.dependency.GraphDependencyAnalyzer;
import com.projectmind.core.domain.DocSection;
import com.projectmind.core.domain.DocSectionType;
import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.GraphEdge;
import com.projectmind.core.domain.GraphEdgeType;
import com.projectmind.core.domain.GraphNode;
import com.projectmind.core.domain.GraphNodeType;
import com.projectmind.core.domain.KnowledgeGraph;
import com.projectmind.core.domain.ProjectMetadata;
import com.projectmind.core.domain.RepositoryFile;
import com.projectmind.core.domain.RepositoryIndex;
import com.projectmind.core.domain.ScanStatus;
import com.projectmind.core.port.MemoryManagerPort;
import com.projectmind.core.port.OllamaClientPort;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TemplateDocumentationGeneratorTest {

    @Test
    void generatesAllSectionsWithGraphContent() {
        MemoryManagerPort memory = mock(MemoryManagerPort.class);
        when(memory.loadSummary(any(), any())).thenReturn(Optional.empty());

        OllamaClientPort ollama = mock(OllamaClientPort.class);
        when(ollama.complete(any())).thenThrow(new RuntimeException("offline"));

        var graphPort = new DefaultKnowledgeGraphBuilder(new GraphDependencyAnalyzer());
        TemplateDocumentationGenerator generator = new TemplateDocumentationGenerator(memory, graphPort, ollama);

        Path repo = Path.of("/tmp/sample");
        ProjectMetadata metadata = new ProjectMetadata(
                "sample", repo.toString(), ScanStatus.INDEXED,
                Instant.now(), Instant.now(), Instant.now(),
                3, 3, "test", Map.of());
        RepositoryIndex index = new RepositoryIndex(
                repo,
                Instant.now(),
                3,
                List.of(
                        file("src/App.java", FileType.JAVA),
                        file("pom.xml", FileType.MAVEN),
                        file("application.yaml", FileType.YAML)));
        KnowledgeGraph graph = new KnowledgeGraph(
                List.of(
                        node("ctrl", "UserController", GraphNodeType.CONTROLLER),
                        node("svc", "UserService", GraphNodeType.SERVICE)),
                List.of(edge("ctrl", "svc", GraphEdgeType.INJECTS)));

        var sections = generator.generateAll(repo, metadata, index, graph);

        assertThat(sections).hasSize(DocSectionType.values().length);

        DocSection controllers = sections.stream()
                .filter(s -> s.type() == DocSectionType.CONTROLLERS)
                .findFirst()
                .orElseThrow();
        assertThat(controllers.markdownContent()).contains("UserController");

        DocSection dependencyGraph = sections.stream()
                .filter(s -> s.type() == DocSectionType.DEPENDENCY_GRAPH)
                .findFirst()
                .orElseThrow();
        assertThat(dependencyGraph.mermaidDiagram()).contains("graph TD");

        DocSection glossary = sections.stream()
                .filter(s -> s.type() == DocSectionType.GLOSSARY)
                .findFirst()
                .orElseThrow();
        assertThat(glossary.markdownContent()).contains("UserController");

        DocSection overview = sections.stream()
                .filter(s -> s.type() == DocSectionType.REPOSITORY_OVERVIEW)
                .findFirst()
                .orElseThrow();
        assertThat(overview.markdownContent()).contains("See also");
    }

    @Test
    void regenerateAffectedReturnsSubset() {
        MemoryManagerPort memory = mock(MemoryManagerPort.class);
        when(memory.loadSummary(any(), any())).thenReturn(Optional.empty());
        OllamaClientPort ollama = mock(OllamaClientPort.class);
        when(ollama.complete(any())).thenThrow(new RuntimeException("offline"));

        var graphPort = new DefaultKnowledgeGraphBuilder(new GraphDependencyAnalyzer());
        TemplateDocumentationGenerator generator = new TemplateDocumentationGenerator(memory, graphPort, ollama);

        Path repo = Path.of("/tmp/sample");
        ProjectMetadata metadata = new ProjectMetadata(
                "sample", repo.toString(), ScanStatus.INDEXED,
                Instant.now(), Instant.now(), Instant.now(),
                1, 1, "test", Map.of());
        RepositoryIndex index = new RepositoryIndex(repo, Instant.now(), 1, List.of(file("App.java", FileType.JAVA)));
        KnowledgeGraph graph = new KnowledgeGraph(
                List.of(node("ctrl", "UserController", GraphNodeType.CONTROLLER, "App.java")),
                List.of());

        var sections = generator.regenerateAffected(
                repo, metadata, index, graph, List.of("App.java"));

        assertThat(sections).isNotEmpty();
        assertThat(sections.size()).isLessThan(DocSectionType.values().length);
        assertThat(sections.stream().map(s -> s.type())).contains(DocSectionType.CONTROLLERS);
    }

    private static RepositoryFile file(String path, FileType type) {
        return new RepositoryFile(Path.of(path), Path.of("/tmp/sample").resolve(path), type, "hash", 100, Instant.now());
    }

    private static GraphNode node(String id, String name, GraphNodeType type) {
        return node(id, name, type, "file.java");
    }

    private static GraphNode node(String id, String name, GraphNodeType type, String source) {
        return new GraphNode(id, name, type, source, "com.example");
    }

    private static GraphEdge edge(String source, String target, GraphEdgeType type) {
        return new GraphEdge(source, target, type, type.name());
    }
}
