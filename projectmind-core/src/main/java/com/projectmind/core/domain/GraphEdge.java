package com.projectmind.core.domain;

/**
 * A directed edge in the project knowledge graph.
 */
public record GraphEdge(
        String sourceId,
        String targetId,
        GraphEdgeType type,
        String label
) {
}
