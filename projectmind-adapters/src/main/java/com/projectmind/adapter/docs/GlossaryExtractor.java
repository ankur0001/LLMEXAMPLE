package com.projectmind.adapter.docs;

import com.projectmind.core.domain.DocSectionType;
import com.projectmind.core.domain.FileSummary;
import com.projectmind.core.domain.GraphNode;
import com.projectmind.core.domain.GraphNodeType;
import com.projectmind.core.domain.KnowledgeGraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Extracts glossary terms from graph nodes and file summaries.
 */
final class GlossaryExtractor {

    private GlossaryExtractor() {
    }

    static List<GlossaryEntry> extract(KnowledgeGraph graph, List<FileSummary> summaries) {
        Set<String> terms = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (GraphNode node : graph.nodes()) {
            if (isGlossaryCandidate(node)) {
                terms.add(node.name());
            }
        }

        for (FileSummary summary : summaries) {
            terms.addAll(summary.keyConcepts());
        }

        List<GlossaryEntry> entries = new ArrayList<>();
        for (String term : terms) {
            if (term == null || term.isBlank() || term.length() < 2) {
                continue;
            }
            entries.add(new GlossaryEntry(term, describeTerm(term, graph)));
        }
        entries.sort(Comparator.comparing(GlossaryEntry::term, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private static boolean isGlossaryCandidate(GraphNode node) {
        return switch (node.type()) {
            case CONTROLLER, SERVICE, REPOSITORY, ENTITY, CONFIGURATION, DATABASE, MODULE -> true;
            default -> false;
        };
    }

    private static String describeTerm(String term, KnowledgeGraph graph) {
        return graph.nodes().stream()
                .filter(node -> term.equals(node.name()))
                .findFirst()
                .map(node -> node.type().name().toLowerCase(Locale.ROOT)
                        + " in "
                        + (node.packageName().isBlank() ? node.sourceFile() : node.packageName()))
                .orElse("Project concept");
    }

    record GlossaryEntry(String term, String definition) {
    }
}
