package com.projectmind.core.domain;

import java.util.List;

/**
 * The complete dependency and knowledge graph for a repository.
 */
public record KnowledgeGraph(
        List<GraphNode> nodes,
        List<GraphEdge> edges
) {
}
