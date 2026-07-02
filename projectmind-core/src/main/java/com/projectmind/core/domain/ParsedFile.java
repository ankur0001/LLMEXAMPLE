package com.projectmind.core.domain;

import java.util.List;

/**
 * Structured output from parsing a single source file.
 */
public record ParsedFile(
        String relativePath,
        FileType fileType,
        String packageName,
        List<ParsedType> types,
        List<ParsedImport> imports,
        List<ParsedMethodCall> methodCalls,
        List<String> annotations
) {
}
