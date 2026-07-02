package com.projectmind.adapter.knowledge;

import com.projectmind.core.domain.GraphEdge;
import com.projectmind.core.domain.GraphNode;
import com.projectmind.core.domain.GraphNodeType;
import com.projectmind.core.domain.KnowledgeGraph;
import com.projectmind.core.domain.ParsedFile;
import com.projectmind.core.port.DependencyAnalyzerPort;
import com.projectmind.core.port.KnowledgeGraphPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds and queries the project knowledge graph from parsed files and dependency analysis.
 */
@Component
public class DefaultKnowledgeGraphBuilder implements KnowledgeGraphPort {

    private static final Logger log = LoggerFactory.getLogger(DefaultKnowledgeGraphBuilder.class);
    private static final Set<GraphNodeType> SPRING_LAYERS = EnumSet.of(
            GraphNodeType.CONTROLLER,
            GraphNodeType.SERVICE,
            GraphNodeType.REPOSITORY);

    private final DependencyAnalyzerPort dependencyAnalyzer;

    public DefaultKnowledgeGraphBuilder(DependencyAnalyzerPort dependencyAnalyzer) {
        this.dependencyAnalyzer = dependencyAnalyzer;
    }

    @Override
    public KnowledgeGraph build(List<ParsedFile> parsedFiles, KnowledgeGraph dependencies) {
        log.debug("Building knowledge graph from {} parsed files", parsedFiles.size());
        return dependencyAnalyzer.analyze(parsedFiles, dependencies);
    }

    @Override
    public List<KnowledgeGraph> queryNeighbors(KnowledgeGraph graph, String nodeId, int depth) {
        int safeDepth = Math.max(1, depth);
        return List.of(GraphQueryEngine.subgraph(graph, nodeId, safeDepth));
    }

    @Override
    public KnowledgeGraph filter(KnowledgeGraph graph, GraphNodeType nodeType, String packagePrefix) {
        return GraphQueryEngine.applyFilters(graph, nodeType, packagePrefix);
    }

    @Override
    public String toMermaidDiagram(KnowledgeGraph graph) {
        var sb = new StringBuilder("graph TD\n");
        Map<GraphNodeType, List<GraphNode>> layerGroups = groupSpringLayers(graph.nodes());

        for (Map.Entry<GraphNodeType, List<GraphNode>> entry : layerGroups.entrySet()) {
            GraphNodeType layer = entry.getKey();
            sb.append("  subgraph ").append(layer.name().toLowerCase()).append("\n");
            for (GraphNode node : entry.getValue()) {
                appendNode(sb, node);
            }
            sb.append("  end\n");
        }

        for (GraphNode node : graph.nodes()) {
            if (layerGroups.values().stream().anyMatch(list -> list.contains(node))) {
                continue;
            }
            appendNode(sb, node);
        }

        for (GraphEdge edge : graph.edges()) {
            String label = edge.label() != null && !edge.label().isBlank()
                    ? edge.label()
                    : edge.type().name();
            sb.append("  ")
                    .append(sanitize(edge.sourceId()))
                    .append(" -->|")
                    .append(escape(label))
                    .append("| ")
                    .append(sanitize(edge.targetId()))
                    .append("\n");
        }
        return sb.toString();
    }

    private static Map<GraphNodeType, List<GraphNode>> groupSpringLayers(List<GraphNode> nodes) {
        Map<GraphNodeType, List<GraphNode>> groups = new LinkedHashMap<>();
        for (GraphNode node : nodes) {
            if (SPRING_LAYERS.contains(node.type())) {
                groups.computeIfAbsent(node.type(), ignored -> new ArrayList<>()).add(node);
            }
        }
        return groups;
    }

    private static void appendNode(StringBuilder sb, GraphNode node) {
        String shapeOpen;
        String shapeClose;
        switch (node.type()) {
            case CONTROLLER -> {
                shapeOpen = "([\"";
                shapeClose = "\"])";
            }
            case SERVICE -> {
                shapeOpen = "[[\"";
                shapeClose = "\"]]";
            }
            case REPOSITORY -> {
                shapeOpen = "[(\"";
                shapeClose = "\")]";
            }
            case DATABASE -> {
                shapeOpen = "[(\"";
                shapeClose = "\")]";
            }
            case CONFIGURATION -> {
                shapeOpen = "{{\"";
                shapeClose = "\"}}";
            }
            default -> {
                shapeOpen = "[\"";
                shapeClose = "\"]";
            }
        }
        sb.append("  ")
                .append(sanitize(node.id()))
                .append(shapeOpen)
                .append(escape(node.name()))
                .append(shapeClose)
                .append("\n");
    }

    private static String sanitize(String id) {
        return id.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private static String escape(String value) {
        return value.replace("\"", "'");
    }
}
