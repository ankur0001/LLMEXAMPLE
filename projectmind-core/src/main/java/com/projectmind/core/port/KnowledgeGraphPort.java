package com.projectmind.core.port;

import com.projectmind.core.domain.GraphNodeType;
import com.projectmind.core.domain.KnowledgeGraph;
import com.projectmind.core.domain.ParsedFile;

import java.util.List;

/**
 * Port for building and querying the project knowledge graph.
 */
public interface KnowledgeGraphPort {

    /**
     * Builds a knowledge graph from parsed files and dependency analysis.
     */
    KnowledgeGraph build(List<ParsedFile> parsedFiles, KnowledgeGraph dependencies);

    /**
     * Queries nodes connected to the given node ID.
     */
    List<KnowledgeGraph> queryNeighbors(KnowledgeGraph graph, String nodeId, int depth);

    /**
     * Filters the graph by node type and/or package prefix.
     */
    KnowledgeGraph filter(KnowledgeGraph graph, GraphNodeType nodeType, String packagePrefix);

    /**
     * Generates a Mermaid diagram representation of the graph.
     */
    String toMermaidDiagram(KnowledgeGraph graph);
}
