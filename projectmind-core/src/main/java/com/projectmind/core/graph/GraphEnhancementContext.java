package com.projectmind.core.graph;

import com.projectmind.core.domain.GraphEdge;
import com.projectmind.core.domain.GraphNode;
import com.projectmind.core.domain.GraphNodeType;
import com.projectmind.core.domain.KnowledgeGraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mutable view of a knowledge graph used by plugins during enhancement.
 */
public final class GraphEnhancementContext {

    private final Map<String, GraphNode> nodes = new LinkedHashMap<>();
    private final List<GraphEdge> edges = new ArrayList<>();

    public GraphEnhancementContext(KnowledgeGraph graph) {
        if (graph != null) {
            graph.nodes().forEach(node -> nodes.put(node.id(), node));
            edges.addAll(graph.edges());
        }
    }

    public Collection<GraphNode> nodes() {
        return nodes.values();
    }

    public List<GraphEdge> edges() {
        return List.copyOf(edges);
    }

    public Optional<GraphNode> findNode(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    public void addOrUpdateNode(GraphNode node) {
        nodes.put(node.id(), node);
    }

    public void updateNodeType(String nodeId, GraphNodeType type) {
        GraphNode node = nodes.get(nodeId);
        if (node != null) {
            nodes.put(nodeId, new GraphNode(
                    node.id(), node.name(), type, node.sourceFile(), node.packageName()));
        }
    }

    public void addEdge(GraphEdge edge) {
        if (edges.stream().noneMatch(existing -> edgesEqual(existing, edge))) {
            edges.add(edge);
        }
    }

    public KnowledgeGraph toGraph() {
        return new KnowledgeGraph(List.copyOf(nodes.values()), List.copyOf(edges));
    }

    private static boolean edgesEqual(GraphEdge a, GraphEdge b) {
        return a.sourceId().equals(b.sourceId())
                && a.targetId().equals(b.targetId())
                && a.type() == b.type();
    }
}
