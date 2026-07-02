package com.projectmind.core.port;

import com.projectmind.core.domain.KnowledgeGraph;
import com.projectmind.core.domain.ParsedFile;

import java.util.List;

/**
 * Port for analyzing dependencies and architectural relationships.
 */
public interface DependencyAnalyzerPort {

    /**
     * Analyzes parsed files and produces dependency relationships.
     *
     * @param parsedFiles all parsed source files
     * @param existing    current knowledge graph, or null for fresh build
     * @return updated knowledge graph with dependency edges
     */
    KnowledgeGraph analyze(List<ParsedFile> parsedFiles, KnowledgeGraph existing);
}
