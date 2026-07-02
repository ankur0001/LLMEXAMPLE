package com.projectmind.adapter.docs;

import com.projectmind.core.domain.GraphEdge;
import com.projectmind.core.domain.GraphEdgeType;
import com.projectmind.core.domain.GraphNode;
import com.projectmind.core.domain.GraphNodeType;
import com.projectmind.core.domain.KnowledgeGraph;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds Mermaid sequence diagrams from Spring layer flows in the knowledge graph.
 */
final class SequenceDiagramBuilder {

    private static final Set<GraphEdgeType> FLOW_TYPES = EnumSet.of(
            GraphEdgeType.INJECTS,
            GraphEdgeType.CALLS,
            GraphEdgeType.DEPENDS_ON);

    private SequenceDiagramBuilder() {
    }

    static String build(KnowledgeGraph graph) {
        Map<String, GraphNode> nodesById = indexNodes(graph);
        List<FlowStep> steps = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (GraphEdge edge : graph.edges()) {
            if (!FLOW_TYPES.contains(edge.type())) {
                continue;
            }
            GraphNode source = nodesById.get(edge.sourceId());
            GraphNode target = nodesById.get(edge.targetId());
            if (!isLayerFlow(source, target)) {
                continue;
            }
            String key = source.name() + "->" + target.name() + ":" + edge.label();
            if (seen.add(key)) {
                steps.add(new FlowStep(source.name(), target.name(), edge.label()));
            }
        }

        if (steps.isEmpty()) {
            return "";
        }

        java.util.LinkedHashSet<String> participants = new java.util.LinkedHashSet<>();
        for (FlowStep step : steps.stream().limit(12).toList()) {
            participants.add(step.source());
            participants.add(step.target());
        }

        var sb = new StringBuilder("sequenceDiagram\n");
        for (String participant : participants) {
            sb.append("  participant ").append(sanitize(participant)).append('\n');
        }
        for (FlowStep step : steps.stream().limit(12).toList()) {
            sb.append("  ")
                    .append(sanitize(step.source()))
                    .append("->>")
                    .append(sanitize(step.target()))
                    .append(": ")
                    .append(step.label() != null && !step.label().isBlank() ? step.label() : "invoke")
                    .append('\n');
        }
        return sb.toString();
    }

    private static boolean isLayerFlow(GraphNode source, GraphNode target) {
        if (source == null || target == null) {
            return false;
        }
        GraphNodeType s = source.type();
        GraphNodeType t = target.type();
        return (s == GraphNodeType.CONTROLLER && (t == GraphNodeType.SERVICE || t == GraphNodeType.REPOSITORY))
                || (s == GraphNodeType.SERVICE && t == GraphNodeType.REPOSITORY);
    }

    private static Map<String, GraphNode> indexNodes(KnowledgeGraph graph) {
        Map<String, GraphNode> nodes = new HashMap<>();
        for (GraphNode node : graph.nodes()) {
            nodes.put(node.id(), node);
        }
        return nodes;
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private record FlowStep(String source, String target, String label) {
    }
}
