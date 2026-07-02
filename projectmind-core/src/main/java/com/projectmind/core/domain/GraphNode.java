package com.projectmind.core.domain;

/**
 * A node in the project knowledge graph.
 */
public record GraphNode(
        String id,
        String name,
        GraphNodeType type,
        String sourceFile,
        String packageName
) {
}
