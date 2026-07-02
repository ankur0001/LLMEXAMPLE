package com.projectmind.adapter.knowledge;

import com.projectmind.core.domain.GraphEdge;
import com.projectmind.core.domain.GraphNode;
import com.projectmind.core.domain.GraphNodeType;
import com.projectmind.core.domain.KnowledgeGraph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Queries subgraphs from a {@link KnowledgeGraph}.
 */
final class GraphQueryEngine {

    private GraphQueryEngine() {
    }

    static KnowledgeGraph subgraph(KnowledgeGraph graph, String nodeId, int depth) {
        if (nodeId == null || nodeId.isBlank()) {
            return graph;
        }

        Map<String, GraphNode> nodeById = indexNodes(graph.nodes());
        if (!nodeById.containsKey(nodeId)) {
            return new KnowledgeGraph(List.of(), List.of());
        }

        Set<String> visitedNodes = new HashSet<>();
        Set<String> collectedEdges = new HashSet<>();
        List<GraphEdge> edges = new ArrayList<>();

        Queue<String> queue = new ArrayDeque<>();
        Map<String, Integer> hopByNode = new HashMap<>();
        queue.add(nodeId);
        hopByNode.put(nodeId, 0);
        visitedNodes.add(nodeId);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int hop = hopByNode.get(current);
            if (hop >= depth) {
                continue;
            }

            for (GraphEdge edge : graph.edges()) {
                String neighbor = null;
                if (edge.sourceId().equals(current)) {
                    neighbor = edge.targetId();
                } else if (edge.targetId().equals(current)) {
                    neighbor = edge.sourceId();
                }
                if (neighbor == null) {
                    continue;
                }

                String edgeKey = edge.sourceId() + "|" + edge.type() + "|" + edge.targetId();
                if (collectedEdges.add(edgeKey)) {
                    edges.add(edge);
                }

                if (visitedNodes.add(neighbor)) {
                    hopByNode.put(neighbor, hop + 1);
                    queue.add(neighbor);
                }
            }
        }

        List<GraphNode> nodes = visitedNodes.stream()
                .map(nodeById::get)
                .filter(java.util.Objects::nonNull)
                .toList();

        return new KnowledgeGraph(nodes, edges);
    }

    static KnowledgeGraph filterByNodeType(KnowledgeGraph graph, GraphNodeType nodeType) {
        if (nodeType == null) {
            return graph;
        }
        List<GraphNode> nodes = graph.nodes().stream()
                .filter(node -> node.type() == nodeType)
                .toList();
        return inducedSubgraph(graph, nodes);
    }

    static KnowledgeGraph filterByPackage(KnowledgeGraph graph, String packagePrefix) {
        if (packagePrefix == null || packagePrefix.isBlank()) {
            return graph;
        }
        List<GraphNode> nodes = graph.nodes().stream()
                .filter(node -> node.packageName() != null && node.packageName().startsWith(packagePrefix))
                .toList();
        return inducedSubgraph(graph, nodes);
    }

    static KnowledgeGraph applyFilters(
            KnowledgeGraph graph,
            GraphNodeType nodeType,
            String packagePrefix) {
        KnowledgeGraph filtered = graph;
        if (nodeType != null) {
            filtered = filterByNodeType(filtered, nodeType);
        }
        if (packagePrefix != null && !packagePrefix.isBlank()) {
            filtered = filterByPackage(filtered, packagePrefix);
        }
        return filtered;
    }

    private static KnowledgeGraph inducedSubgraph(KnowledgeGraph graph, List<GraphNode> nodes) {
        Set<String> nodeIds = nodes.stream().map(GraphNode::id).collect(Collectors.toSet());
        List<GraphEdge> edges = graph.edges().stream()
                .filter(edge -> nodeIds.contains(edge.sourceId()) && nodeIds.contains(edge.targetId()))
                .toList();
        return new KnowledgeGraph(nodes, edges);
    }

    private static Map<String, GraphNode> indexNodes(List<GraphNode> nodes) {
        Map<String, GraphNode> nodeById = new LinkedHashMap<>();
        for (GraphNode node : nodes) {
            nodeById.put(node.id(), node);
        }
        return nodeById;
    }
}
