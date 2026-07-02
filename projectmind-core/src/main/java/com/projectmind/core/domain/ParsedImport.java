package com.projectmind.core.domain;

/**
 * An import statement extracted from a source file.
 */
public record ParsedImport(
        String importedName,
        boolean isStatic,
        int line
) {
}
