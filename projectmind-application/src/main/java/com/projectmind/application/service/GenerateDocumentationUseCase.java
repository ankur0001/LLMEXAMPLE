package com.projectmind.application.service;

import com.projectmind.core.domain.DocSection;
import com.projectmind.core.domain.DocSectionType;
import com.projectmind.core.domain.KnowledgeGraph;
import com.projectmind.core.domain.ProjectMetadata;
import com.projectmind.core.domain.RepositoryIndex;
import com.projectmind.core.port.DocumentationGeneratorPort;
import com.projectmind.core.port.HtmlGeneratorPort;
import com.projectmind.core.port.MemoryManagerPort;
import com.projectmind.core.port.PluginRegistryPort;
import com.projectmind.core.port.ProjectMindPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates documentation generation and HTML site rendering.
 */
public class GenerateDocumentationUseCase {

    private static final Logger log = LoggerFactory.getLogger(GenerateDocumentationUseCase.class);

    private final MemoryManagerPort memoryManager;
    private final DocumentationGeneratorPort docGenerator;
    private final HtmlGeneratorPort htmlGenerator;
    private final PluginRegistryPort pluginRegistry;

    public GenerateDocumentationUseCase(
            MemoryManagerPort memoryManager,
            DocumentationGeneratorPort docGenerator,
            HtmlGeneratorPort htmlGenerator,
            PluginRegistryPort pluginRegistry) {
        this.memoryManager = memoryManager;
        this.docGenerator = docGenerator;
        this.htmlGenerator = htmlGenerator;
        this.pluginRegistry = pluginRegistry;
    }

    /**
     * Generates all documentation sections and renders the HTML site.
     */
    public Path execute(Path repositoryPath) {
        log.info("Generating documentation for: {}", repositoryPath);

        ProjectMetadata metadata = memoryManager.loadMetadata(repositoryPath)
                .orElseThrow(() -> new IllegalStateException("Repository not scanned."));
        RepositoryIndex index = memoryManager.loadIndex(repositoryPath)
                .orElseThrow(() -> new IllegalStateException("No file index found."));
        KnowledgeGraph graph = memoryManager.loadGraph(repositoryPath)
                .orElse(new KnowledgeGraph(List.of(), List.of()));

        List<DocSection> sections = docGenerator.generateAll(
                repositoryPath, metadata, index, graph);
        sections = mergePluginSections(repositoryPath, metadata, index, graph, sections);
        return persistAndRender(repositoryPath, metadata, sections);
    }

    /**
     * Regenerates only documentation sections affected by changed files.
     */
    public Path regenerateChanged(Path repositoryPath, List<String> changedFiles) {
        if (changedFiles == null || changedFiles.isEmpty()) {
            return execute(repositoryPath);
        }

        ProjectMetadata metadata = memoryManager.loadMetadata(repositoryPath)
                .orElseThrow(() -> new IllegalStateException("Repository not scanned."));
        RepositoryIndex index = memoryManager.loadIndex(repositoryPath)
                .orElseThrow(() -> new IllegalStateException("No file index found."));
        KnowledgeGraph graph = memoryManager.loadGraph(repositoryPath)
                .orElse(new KnowledgeGraph(List.of(), List.of()));

        List<DocSection> existing = memoryManager.loadDocumentationSections(repositoryPath)
                .orElseGet(() -> docGenerator.generateAll(repositoryPath, metadata, index, graph));

        List<DocSection> updated = docGenerator.regenerateAffected(
                repositoryPath, metadata, index, graph, changedFiles);
        List<DocSection> merged = mergeSections(existing, updated);
        merged = mergePluginSections(repositoryPath, metadata, index, graph, merged);
        log.info("Regenerated {} documentation sections for {} changed files",
                updated.size(), changedFiles.size());
        return persistAndRender(repositoryPath, metadata, merged);
    }

    private Path persistAndRender(Path repositoryPath, ProjectMetadata metadata, List<DocSection> sections) {
        memoryManager.saveDocumentationSections(repositoryPath, sections);
        Path outputPath = htmlGenerator.generate(repositoryPath, metadata, sections);
        log.info("Documentation generated at: {}", outputPath);
        return outputPath;
    }

    private List<DocSection> mergePluginSections(
            Path repositoryPath,
            ProjectMetadata metadata,
            RepositoryIndex index,
            KnowledgeGraph graph,
            List<DocSection> sections) {
        List<DocSection> pluginSections = new ArrayList<>();
        for (ProjectMindPlugin plugin : pluginRegistry.getEnabledPlugins()) {
            pluginSections.addAll(plugin.generateDocSections(repositoryPath, metadata, index, graph));
        }
        if (pluginSections.isEmpty()) {
            return sections;
        }
        return mergeSections(sections, pluginSections);
    }

    private static List<DocSection> mergeSections(List<DocSection> existing, List<DocSection> updated) {
        Map<DocSectionType, DocSection> byType = new LinkedHashMap<>();
        for (DocSection section : existing) {
            byType.put(section.type(), section);
        }
        for (DocSection section : updated) {
            byType.put(section.type(), section);
        }
        return new ArrayList<>(byType.values());
    }
}
