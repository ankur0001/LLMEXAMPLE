package com.projectmind.core.domain;

import java.util.List;

/**
 * A parsed type declaration (class, interface, or enum).
 */
public record ParsedType(
        String name,
        GraphNodeType kind,
        String superClass,
        List<String> interfaces,
        List<ParsedMethod> methods,
        List<String> annotations,
        int startLine,
        int endLine
) {
}
