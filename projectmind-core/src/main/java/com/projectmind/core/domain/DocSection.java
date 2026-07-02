package com.projectmind.core.domain;

/**
 * A single section of generated documentation.
 */
public record DocSection(
        DocSectionType type,
        String title,
        String markdownContent,
        String mermaidDiagram
) {
}
