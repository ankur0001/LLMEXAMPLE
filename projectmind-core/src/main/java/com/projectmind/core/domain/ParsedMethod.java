package com.projectmind.core.domain;

import java.util.List;

/**
 * A parsed method declaration within a type.
 */
public record ParsedMethod(
        String name,
        String returnType,
        List<String> parameters,
        List<String> annotations,
        int startLine,
        int endLine
) {
}
