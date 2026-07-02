package com.projectmind.core.port;

import com.projectmind.core.domain.DocSection;
import com.projectmind.core.domain.KnowledgeGraph;
import com.projectmind.core.domain.ProjectMetadata;
import com.projectmind.core.domain.RepositoryIndex;

import java.nio.file.Path;
import java.util.List;

/**
 * Port for generating documentation sections from project knowledge.
 */
public interface DocumentationGeneratorPort {

    /**
     * Generates all documentation sections for a repository.
     */
    List<DocSection> generateAll(
            Path repositoryPath,
            ProjectMetadata metadata,
            RepositoryIndex index,
            KnowledgeGraph graph
    );

    /**
     * Regenerates only the sections affected by changed files.
     */
    List<DocSection> regenerateAffected(
            Path repositoryPath,
            ProjectMetadata metadata,
            RepositoryIndex index,
            KnowledgeGraph graph,
            List<String> changedFiles
    );
}
