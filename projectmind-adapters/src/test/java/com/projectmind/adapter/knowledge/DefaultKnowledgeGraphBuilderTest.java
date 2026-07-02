package com.projectmind.adapter.knowledge;

import com.projectmind.core.domain.GraphEdge;
import com.projectmind.core.domain.GraphEdgeType;
import com.projectmind.core.domain.GraphNode;
import com.projectmind.core.domain.GraphNodeType;
import com.projectmind.core.domain.KnowledgeGraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultKnowledgeGraphBuilderTest {

    private final DefaultKnowledgeGraphBuilder builder = new DefaultKnowledgeGraphBuilder(
            (parsedFiles, existing) -> new KnowledgeGraph(
                    List.of(
                            node("ctrl", "UserController", GraphNodeType.CONTROLLER),
                            node("svc", "UserService", GraphNodeType.SERVICE)),
                    List.of(edge("ctrl", "svc", GraphEdgeType.INJECTS))));

    @Test
    void generatesMermaidWithSpringSubgraphs() {
        String mermaid = builder.toMermaidDiagram(new KnowledgeGraph(
                List.of(
                        node("ctrl", "UserController", GraphNodeType.CONTROLLER),
                        node("svc", "UserService", GraphNodeType.SERVICE),
                        node("db", "users", GraphNodeType.DATABASE)),
                List.of(edge("ctrl", "svc", GraphEdgeType.INJECTS))));

        assertThat(mermaid).startsWith("graph TD");
        assertThat(mermaid).contains("subgraph controller");
        assertThat(mermaid).contains("subgraph service");
        assertThat(mermaid).contains("UserController");
        assertThat(mermaid).contains("INJECTS");
    }

    @Test
    void filtersGraphByNodeType() {
        KnowledgeGraph graph = new KnowledgeGraph(
                List.of(
                        node("ctrl", "UserController", GraphNodeType.CONTROLLER),
                        node("svc", "UserService", GraphNodeType.SERVICE)),
                List.of(edge("ctrl", "svc", GraphEdgeType.INJECTS)));

        KnowledgeGraph filtered = builder.filter(graph, GraphNodeType.CONTROLLER, null);

        assertThat(filtered.nodes()).extracting(GraphNode::id).containsExactly("ctrl");
        assertThat(filtered.edges()).isEmpty();
    }

    private static GraphNode node(String id, String name, GraphNodeType type) {
        return new GraphNode(id, name, type, "file.java", "com.example");
    }

    private static GraphEdge edge(String source, String target, GraphEdgeType type) {
        return new GraphEdge(source, target, type, type.name());
    }
}
