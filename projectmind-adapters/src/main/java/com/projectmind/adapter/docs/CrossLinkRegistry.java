package com.projectmind.adapter.docs;

import com.projectmind.core.domain.DocSectionType;

import java.util.Locale;
import java.util.Map;

/**
 * Generates cross-links between documentation sections.
 */
final class CrossLinkRegistry {

    private static final Map<DocSectionType, String> RELATED = Map.ofEntries(
            Map.entry(DocSectionType.REPOSITORY_OVERVIEW, "architecture|technology-stack|developer-guide"),
            Map.entry(DocSectionType.ARCHITECTURE, "dependency-graph|controllers|services|repositories"),
            Map.entry(DocSectionType.CONTROLLERS, "api-documentation|services|architecture"),
            Map.entry(DocSectionType.SERVICES, "repositories|entities|architecture"),
            Map.entry(DocSectionType.REPOSITORIES, "database|entities|services"),
            Map.entry(DocSectionType.ENTITIES, "database|repositories"),
            Map.entry(DocSectionType.SECURITY, "configuration|architecture"),
            Map.entry(DocSectionType.CONFIGURATION, "startup-flow|technology-stack"),
            Map.entry(DocSectionType.DEPENDENCY_GRAPH, "architecture|sequence-diagrams"),
            Map.entry(DocSectionType.SEQUENCE_DIAGRAMS, "controllers|services|repositories"),
            Map.entry(DocSectionType.DEVELOPER_GUIDE, "repository-overview|architecture|glossary"),
            Map.entry(DocSectionType.GLOSSARY, "architecture|developer-guide"));

    private CrossLinkRegistry() {
    }

    static String seeAlso(DocSectionType section) {
        String related = RELATED.get(section);
        if (related == null || related.isBlank()) {
            return "";
        }
        var sb = new StringBuilder("\n\n**See also:** ");
        String[] targets = related.split("\\|");
        for (int i = 0; i < targets.length; i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append('[').append(formatTitle(targets[i])).append("](#").append(targets[i]).append(')');
        }
        return sb.toString();
    }

    static String anchor(DocSectionType type) {
        return type.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    static String formatTitle(DocSectionType type) {
        return type.name().replace('_', ' ');
    }

    private static String formatTitle(String anchor) {
        return anchor.replace('-', ' ');
    }
}
