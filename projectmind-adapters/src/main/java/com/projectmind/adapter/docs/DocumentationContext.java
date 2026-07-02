package com.projectmind.adapter.docs;

import com.projectmind.core.domain.DocSectionType;
import com.projectmind.core.domain.FileSummary;
import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.GraphEdge;
import com.projectmind.core.domain.GraphEdgeType;
import com.projectmind.core.domain.GraphNode;
import com.projectmind.core.domain.GraphNodeType;
import com.projectmind.core.domain.KnowledgeGraph;
import com.projectmind.core.domain.ProjectMetadata;
import com.projectmind.core.domain.RepositoryFile;
import com.projectmind.core.domain.RepositoryIndex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aggregates repository data used to build documentation sections.
 */
final class DocumentationContext {

    private final Path repositoryPath;
    private final ProjectMetadata metadata;
    private final RepositoryIndex index;
    private final KnowledgeGraph graph;
    private final Map<String, FileSummary> summariesByPath;

    DocumentationContext(
            Path repositoryPath,
            ProjectMetadata metadata,
            RepositoryIndex index,
            KnowledgeGraph graph,
            Map<String, FileSummary> summariesByPath) {
        this.repositoryPath = repositoryPath;
        this.metadata = metadata;
        this.index = index;
        this.graph = graph;
        this.summariesByPath = summariesByPath != null ? summariesByPath : Map.of();
    }

    Path repositoryPath() {
        return repositoryPath;
    }

    ProjectMetadata metadata() {
        return metadata;
    }

    RepositoryIndex index() {
        return index;
    }

    KnowledgeGraph graph() {
        return graph;
    }

    List<GraphNode> nodesOfType(GraphNodeType type) {
        return graph.nodes().stream()
                .filter(node -> node.type() == type)
                .sorted(Comparator.comparing(GraphNode::name))
                .toList();
    }

    List<GraphNode> nodesInFile(String relativePath) {
        return graph.nodes().stream()
                .filter(node -> relativePath.equals(node.sourceFile()))
                .toList();
    }

    List<GraphEdge> edgesOfType(GraphEdgeType type) {
        return graph.edges().stream()
                .filter(edge -> edge.type() == type)
                .toList();
    }

    Map<GraphNodeType, Long> nodeCountsByType() {
        return graph.nodes().stream()
                .collect(Collectors.groupingBy(GraphNode::type, Collectors.counting()));
    }

    List<RepositoryFile> filesOfType(FileType type) {
        return index.files().stream()
                .filter(file -> file.fileType() == type)
                .sorted(Comparator.comparing(file -> file.relativePath().toString()))
                .toList();
    }

    String summaryFor(String relativePath) {
        FileSummary summary = summariesByPath.get(relativePath);
        if (summary == null || summary.summary() == null || summary.summary().isBlank()) {
            return "";
        }
        return summary.summary();
    }

    List<FileSummary> summaries() {
        return List.copyOf(summariesByPath.values());
    }

    String buildContextBundle() {
        var sb = new StringBuilder();
        sb.append("Project: ").append(metadata.name()).append('\n');
        sb.append("Files: ").append(metadata.totalFiles()).append('\n');
        sb.append("Graph nodes: ").append(graph.nodes().size()).append('\n');
        sb.append("Graph edges: ").append(graph.edges().size()).append('\n');

        appendNodeList(sb, "Controllers", nodesOfType(GraphNodeType.CONTROLLER));
        appendNodeList(sb, "Services", nodesOfType(GraphNodeType.SERVICE));
        appendNodeList(sb, "Repositories", nodesOfType(GraphNodeType.REPOSITORY));
        appendNodeList(sb, "Entities", nodesOfType(GraphNodeType.ENTITY));

        sb.append("\nFile types:\n");
        index.statistics().countByType().forEach((type, count) ->
                sb.append("- ").append(type).append(": ").append(count).append('\n'));

        sb.append("\nSample summaries:\n");
        summariesByPath.values().stream()
                .limit(8)
                .forEach(summary -> sb.append("- ")
                        .append(summary.relativePath())
                        .append(": ")
                        .append(truncate(summary.summary(), 200))
                        .append('\n'));
        return sb.toString();
    }

    Set<DocSectionType> sectionsAffectedBy(String changedFile) {
        Set<DocSectionType> affected = EnumSet.noneOf(DocSectionType.class);
        affected.add(DocSectionType.REPOSITORY_OVERVIEW);
        affected.add(DocSectionType.DEPENDENCY_GRAPH);
        affected.add(DocSectionType.GLOSSARY);

        String lower = changedFile.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java") || lower.endsWith(".kt")) {
            affected.add(DocSectionType.ARCHITECTURE);
            affected.add(DocSectionType.CODE_QUALITY);
            affected.add(DocSectionType.DEVELOPER_GUIDE);
            affected.add(DocSectionType.SEQUENCE_DIAGRAMS);
            affected.add(DocSectionType.DESIGN_PATTERNS);
        }
        if (lower.endsWith("pom.xml") || lower.endsWith(".gradle") || lower.endsWith(".gradle.kts")) {
            affected.add(DocSectionType.TECHNOLOGY_STACK);
            affected.add(DocSectionType.EXTERNAL_INTEGRATIONS);
        }
        if (lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".properties")) {
            affected.add(DocSectionType.CONFIGURATION);
            affected.add(DocSectionType.STARTUP_FLOW);
        }
        if (lower.endsWith(".sql")) {
            affected.add(DocSectionType.DATABASE);
        }
        if (lower.contains("docker") || lower.contains("k8s") || lower.contains("kubernetes")) {
            affected.add(DocSectionType.EXTERNAL_INTEGRATIONS);
        }

        for (GraphNode node : nodesInFile(changedFile)) {
            switch (node.type()) {
                case CONTROLLER -> {
                    affected.add(DocSectionType.CONTROLLERS);
                    affected.add(DocSectionType.API_DOCUMENTATION);
                    affected.add(DocSectionType.ARCHITECTURE);
                    affected.add(DocSectionType.SEQUENCE_DIAGRAMS);
                }
                case SERVICE -> {
                    affected.add(DocSectionType.SERVICES);
                    affected.add(DocSectionType.ARCHITECTURE);
                    affected.add(DocSectionType.SEQUENCE_DIAGRAMS);
                }
                case REPOSITORY -> {
                    affected.add(DocSectionType.REPOSITORIES);
                    affected.add(DocSectionType.DATABASE);
                    affected.add(DocSectionType.SEQUENCE_DIAGRAMS);
                }
                case ENTITY -> {
                    affected.add(DocSectionType.ENTITIES);
                    affected.add(DocSectionType.DATABASE);
                }
                case FILTER -> affected.add(DocSectionType.SECURITY);
                case CONFIGURATION -> affected.add(DocSectionType.CONFIGURATION);
                case DATABASE -> affected.add(DocSectionType.DATABASE);
                default -> { }
            }
        }
        return affected;
    }

    private static void appendNodeList(StringBuilder sb, String label, List<GraphNode> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        sb.append('\n').append(label).append(":\n");
        nodes.stream().limit(20).forEach(node -> sb.append("- ")
                .append(node.name())
                .append(" (")
                .append(node.sourceFile())
                .append(")\n"));
    }

    private static String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text != null ? text : "";
        }
        return text.substring(0, max) + "...";
    }
}
