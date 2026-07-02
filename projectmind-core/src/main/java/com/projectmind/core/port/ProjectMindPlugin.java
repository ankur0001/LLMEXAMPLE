package com.projectmind.core.port;

import com.projectmind.core.domain.DocSection;
import com.projectmind.core.domain.DocSectionType;
import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.KnowledgeGraph;
import com.projectmind.core.domain.ParsedFile;
import com.projectmind.core.domain.ProjectMetadata;
import com.projectmind.core.domain.RepositoryIndex;
import com.projectmind.core.graph.GraphEnhancementContext;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Port for framework-specific plugin extensions.
 */
public interface ProjectMindPlugin {

    /**
     * Unique plugin identifier.
     */
    String getName();

    /**
     * Returns true when this plugin should process the given file.
     */
    default boolean appliesTo(ParsedFile file) {
        return file != null && supportedFileTypes().contains(file.fileType());
    }

    /**
     * File types this plugin understands.
     */
    default Set<FileType> supportedFileTypes() {
        return Set.of(FileType.JAVA, FileType.KOTLIN);
    }

    /**
     * Enhances the knowledge graph with plugin-specific relationships for one parsed file.
     */
    void enhance(GraphEnhancementContext context, ParsedFile file);

    /**
     * Post-processes the graph after all files have been enhanced.
     */
    default void finalizeGraph(GraphEnhancementContext context) {
    }

    /**
     * Called when the plugin is loaded and enabled.
     */
    default void onLoad(PluginContext context) {
    }

    /**
     * Called when the application shuts down.
     */
    default void onShutdown() {
    }

    /**
     * Returns additional documentation section types this plugin provides.
     */
    default List<DocSectionType> additionalSections() {
        return List.of();
    }

    /**
     * Generates plugin-specific documentation sections.
     */
    default List<DocSection> generateDocSections(
            Path repositoryPath,
            ProjectMetadata metadata,
            RepositoryIndex index,
            KnowledgeGraph graph) {
        return List.of();
    }
}
