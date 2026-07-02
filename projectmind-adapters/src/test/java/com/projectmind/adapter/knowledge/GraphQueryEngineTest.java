package com.projectmind.adapter.knowledge;

import com.projectmind.core.domain.GraphEdge;
import com.projectmind.core.domain.GraphEdgeType;
import com.projectmind.core.domain.GraphNode;
import com.projectmind.core.domain.GraphNodeType;
import com.projectmind.core.domain.KnowledgeGraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GraphQueryEngineTest {

    @Test
    void returnsNeighborhoodSubgraph() {
        KnowledgeGraph graph = new KnowledgeGraph(
                List.of(
                        node("a", "A"),
                        node("b", "B"),
                        node("c", "C")),
                List.of(
                        edge("a", "b", GraphEdgeType.CALLS),
                        edge("b", "c", GraphEdgeType.INJECTS)));

        KnowledgeGraph subgraph = GraphQueryEngine.subgraph(graph, "b", 1);

        assertThat(subgraph.nodes()).extracting(GraphNode::id).containsExactlyInAnyOrder("a", "b", "c");
        assertThat(subgraph.edges()).hasSize(2);
    }

    @Test
    void returnsEmptyGraphForUnknownNode() {
        KnowledgeGraph graph = new KnowledgeGraph(List.of(node("a", "A")), List.of());

        KnowledgeGraph subgraph = GraphQueryEngine.subgraph(graph, "missing", 1);

        assertThat(subgraph.nodes()).isEmpty();
        assertThat(subgraph.edges()).isEmpty();
    }

    @Test
    void filtersByNodeType() {
        KnowledgeGraph graph = new KnowledgeGraph(
                List.of(
                        node("a", "A", GraphNodeType.CONTROLLER),
                        node("b", "B", GraphNodeType.SERVICE)),
                List.of(edge("a", "b", GraphEdgeType.INJECTS)));

        KnowledgeGraph filtered = GraphQueryEngine.filterByNodeType(graph, GraphNodeType.SERVICE);

        assertThat(filtered.nodes()).extracting(GraphNode::id).containsExactly("b");
        assertThat(filtered.edges()).isEmpty();
    }

    @Test
    void filtersByPackagePrefix() {
        KnowledgeGraph graph = new KnowledgeGraph(
                List.of(
                        node("a", "A", GraphNodeType.CLASS, "com.example.web"),
                        node("b", "B", GraphNodeType.CLASS, "com.other")),
                List.of(edge("a", "b", GraphEdgeType.CALLS)));

        KnowledgeGraph filtered = GraphQueryEngine.filterByPackage(graph, "com.example");

        assertThat(filtered.nodes()).extracting(GraphNode::id).containsExactly("a");
        assertThat(filtered.edges()).isEmpty();
    }

    private static GraphNode node(String id, String name) {
        return node(id, name, GraphNodeType.CLASS, "pkg");
    }

    private static GraphNode node(String id, String name, GraphNodeType type) {
        return node(id, name, type, "pkg");
    }

    private static GraphNode node(String id, String name, GraphNodeType type, String pkg) {
        return new GraphNode(id, name, type, "file.java", pkg);
    }

    private static GraphEdge edge(String source, String target, GraphEdgeType type) {
        return new GraphEdge(source, target, type, type.name());
    }
}
