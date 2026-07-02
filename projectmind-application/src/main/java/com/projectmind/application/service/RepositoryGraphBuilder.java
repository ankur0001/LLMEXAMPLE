package com.projectmind.application.service;

import com.projectmind.core.concurrent.ParallelExecutor;
import com.projectmind.core.domain.FileChangeSet;
import com.projectmind.core.domain.GraphEdge;
import com.projectmind.core.domain.GraphNode;
import com.projectmind.core.domain.KnowledgeGraph;
import com.projectmind.core.domain.ParsedFile;
import com.projectmind.core.domain.RepositoryFile;
import com.projectmind.core.domain.RepositoryIndex;
import com.projectmind.core.port.ConfigurationPort;
import com.projectmind.core.port.KnowledgeGraphPort;
import com.projectmind.core.port.LanguageParserPort;
import com.projectmind.core.port.MemoryManagerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Parses indexed repository files and persists the dependency graph.
 */
public class RepositoryGraphBuilder {

    private static final Logger log = LoggerFactory.getLogger(RepositoryGraphBuilder.class);

    private final LanguageParserPort languageParser;
    private final KnowledgeGraphPort knowledgeGraphPort;
    private final MemoryManagerPort memoryManager;
    private final PluginEnhancementService pluginEnhancementService;
    private final ConfigurationPort configuration;

    public RepositoryGraphBuilder(
            LanguageParserPort languageParser,
            KnowledgeGraphPort knowledgeGraphPort,
            MemoryManagerPort memoryManager,
            PluginEnhancementService pluginEnhancementService,
            ConfigurationPort configuration) {
        this.languageParser = languageParser;
        this.knowledgeGraphPort = knowledgeGraphPort;
        this.memoryManager = memoryManager;
        this.pluginEnhancementService = pluginEnhancementService;
        this.configuration = configuration;
    }

    /**
     * Parses supported files from the stored index and writes {@code dependency_graph.json}.
     */
    public KnowledgeGraph buildAndPersist(Path repositoryPath) {
        RepositoryIndex index = memoryManager.loadIndex(repositoryPath)
                .orElse(null);
        if (index == null || index.files().isEmpty()) {
            log.debug("No repository index found for graph build: {}", repositoryPath);
            return new KnowledgeGraph(List.of(), List.of());
        }

        List<ParsedFile> parsedFiles = parseIndexedFiles(index);
        KnowledgeGraph existing = memoryManager.loadGraph(repositoryPath).orElse(null);
        KnowledgeGraph graph = knowledgeGraphPort.build(parsedFiles, existing);
        graph = pluginEnhancementService.enhance(graph, parsedFiles);
        memoryManager.saveGraph(repositoryPath, graph);
        persistMermaidDiagram(repositoryPath, graph);
        log.info("Dependency graph saved: {} nodes, {} edges ({} parsed files)",
                graph.nodes().size(), graph.edges().size(), parsedFiles.size());
        return graph;
    }

    /**
     * Re-parses only changed files and merges the result into the existing graph.
     */
    public KnowledgeGraph updateChangedFiles(Path repositoryPath, FileChangeSet changes) {
        if (!changes.hasChanges()) {
            return memoryManager.loadGraph(repositoryPath).orElse(new KnowledgeGraph(List.of(), List.of()));
        }

        KnowledgeGraph existing = memoryManager.loadGraph(repositoryPath).orElse(new KnowledgeGraph(List.of(), List.of()));
        existing = removeDeletedFiles(existing, changes.deleted());

        List<ParsedFile> parsedFiles = parseRepositoryFiles(changes.changedFiles());
        KnowledgeGraph graph = knowledgeGraphPort.build(parsedFiles, existing);
        graph = pluginEnhancementService.enhance(graph, parsedFiles);
        memoryManager.saveGraph(repositoryPath, graph);
        persistMermaidDiagram(repositoryPath, graph);
        log.info("Incremental graph update: {} nodes, {} edges ({} changed files)",
                graph.nodes().size(), graph.edges().size(), changes.changedFiles().size());
        return graph;
    }

    private static KnowledgeGraph removeDeletedFiles(KnowledgeGraph graph, List<String> deletedPaths) {
        if (deletedPaths.isEmpty()) {
            return graph;
        }
        Set<String> deleted = Set.copyOf(deletedPaths);
        List<GraphNode> nodes = graph.nodes().stream()
                .filter(node -> node.sourceFile() == null || !deleted.contains(node.sourceFile()))
                .toList();
        Set<String> nodeIds = nodes.stream().map(GraphNode::id).collect(Collectors.toSet());
        List<GraphEdge> edges = graph.edges().stream()
                .filter(edge -> nodeIds.contains(edge.sourceId()) && nodeIds.contains(edge.targetId()))
                .toList();
        return new KnowledgeGraph(nodes, edges);
    }

    private void persistMermaidDiagram(Path repositoryPath, KnowledgeGraph graph) {
        try {
            Path diagramsDir = repositoryPath.resolve(".ai-memory/diagrams");
            Files.createDirectories(diagramsDir);
            Files.writeString(
                    diagramsDir.resolve("dependency_graph.mmd"),
                    knowledgeGraphPort.toMermaidDiagram(graph));
        } catch (IOException ex) {
            log.warn("Failed to write dependency graph Mermaid diagram: {}", ex.getMessage());
        }
    }

    private List<ParsedFile> parseIndexedFiles(RepositoryIndex index) {
        return parseRepositoryFiles(index.files());
    }

    private List<ParsedFile> parseRepositoryFiles(List<RepositoryFile> files) {
        List<RepositoryFile> parseable = new ArrayList<>();
        for (RepositoryFile file : files) {
            if (languageParser.supports(file.fileType())) {
                parseable.add(file);
            }
        }
        if (parseable.isEmpty()) {
            return List.of();
        }

        return ParallelExecutor.invokeAll(
                parseable.stream()
                        .map(file -> (Callable<ParsedFile>) () -> parseFile(file))
                        .toList(),
                configuration.getParseConcurrency()).stream()
                .filter(parsed -> parsed != null)
                .toList();
    }

    private ParsedFile parseFile(RepositoryFile file) {
        try {
            String source = Files.readString(file.absolutePath());
            return languageParser.parse(file.relativePath(), file.fileType(), source);
        } catch (IOException ex) {
            log.warn("Failed to parse {} for dependency analysis: {}", file.relativePath(), ex.getMessage());
            return null;
        }
    }
}
