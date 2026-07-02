package com.projectmind.application.service;

import com.projectmind.core.domain.GraphNodeType;
import com.projectmind.core.domain.KnowledgeGraph;
import com.projectmind.core.port.KnowledgeGraphPort;
import com.projectmind.core.port.MemoryManagerPort;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Loads and queries the persisted dependency graph.
 */
public class GraphQueryUseCase {

    private final MemoryManagerPort memoryManager;
    private final KnowledgeGraphPort knowledgeGraphPort;

    public GraphQueryUseCase(MemoryManagerPort memoryManager, KnowledgeGraphPort knowledgeGraphPort) {
        this.memoryManager = memoryManager;
        this.knowledgeGraphPort = knowledgeGraphPort;
    }

    /**
     * Returns the full graph or a neighborhood subgraph when {@code nodeId} is provided.
     */
    public Optional<KnowledgeGraph> execute(
            Path repositoryPath,
            String nodeId,
            int depth,
            GraphNodeType nodeType,
            String packagePrefix) {
        return memoryManager.loadGraph(repositoryPath).map(graph -> {
            KnowledgeGraph result = graph;
            if (nodeId != null && !nodeId.isBlank()) {
                result = knowledgeGraphPort.queryNeighbors(result, nodeId, depth).get(0);
            }
            return knowledgeGraphPort.filter(result, nodeType, packagePrefix);
        });
    }

    /**
     * Generates a Mermaid diagram for the graph or a filtered subgraph.
     */
    public Optional<String> mermaid(
            Path repositoryPath,
            String nodeId,
            int depth,
            GraphNodeType nodeType,
            String packagePrefix) {
        return execute(repositoryPath, nodeId, depth, nodeType, packagePrefix)
                .map(knowledgeGraphPort::toMermaidDiagram);
    }
}
