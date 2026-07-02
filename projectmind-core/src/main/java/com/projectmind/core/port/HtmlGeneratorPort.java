package com.projectmind.core.port;

import com.projectmind.core.domain.DocSection;
import com.projectmind.core.domain.ProjectMetadata;

import java.nio.file.Path;
import java.util.List;

/**
 * Port for rendering documentation sections into a static HTML website.
 */
public interface HtmlGeneratorPort {

    /**
     * Generates the complete HTML documentation site.
     *
     * @param repositoryPath repository root
     * @param metadata       project metadata
     * @param sections       documentation sections to render
     * @return path to the generated index.html
     */
    Path generate(Path repositoryPath, ProjectMetadata metadata, List<DocSection> sections);
}
