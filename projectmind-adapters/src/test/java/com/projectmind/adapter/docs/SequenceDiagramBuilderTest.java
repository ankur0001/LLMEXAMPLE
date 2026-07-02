package com.projectmind.adapter.docs;

import com.projectmind.core.domain.GraphEdge;
import com.projectmind.core.domain.GraphEdgeType;
import com.projectmind.core.domain.GraphNode;
import com.projectmind.core.domain.GraphNodeType;
import com.projectmind.core.domain.KnowledgeGraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SequenceDiagramBuilderTest {

    @Test
    void buildsSequenceFromLayerFlows() {
        KnowledgeGraph graph = new KnowledgeGraph(
                List.of(
                        node("ctrl", "UserController", GraphNodeType.CONTROLLER),
                        node("svc", "UserService", GraphNodeType.SERVICE),
                        node("repo", "UserRepository", GraphNodeType.REPOSITORY)),
                List.of(
                        edge("ctrl", "svc", GraphEdgeType.INJECTS, "findAll"),
                        edge("svc", "repo", GraphEdgeType.CALLS, "findAll")));

        String mermaid = SequenceDiagramBuilder.build(graph);

        assertThat(mermaid).contains("sequenceDiagram");
        assertThat(mermaid).contains("UserController");
        assertThat(mermaid).contains("UserService");
        assertThat(mermaid).contains("UserRepository");
    }

    private static GraphNode node(String id, String name, GraphNodeType type) {
        return new GraphNode(id, name, type, "file.java", "com.example");
    }

    private static GraphEdge edge(String source, String target, GraphEdgeType type, String label) {
        return new GraphEdge(source, target, type, label);
    }
}
