package com.projectmind.application.service;

import com.projectmind.core.domain.FileSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses structured Ollama file summary responses.
 */
final class FileSummaryParser {

    private FileSummaryParser() {
    }

    static FileSummary parse(String relativePath, String response) {
        if (response == null || response.isBlank()) {
            return new FileSummary(relativePath, "", "", List.of());
        }

        String summary = extractSection(response, "SUMMARY");
        String purpose = extractSection(response, "PURPOSE");
        String conceptsRaw = extractSection(response, "KEY_CONCEPTS");
        if (summary.isBlank()) {
            summary = response.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .findFirst()
                    .orElse("");
        }

        List<String> concepts = parseConcepts(conceptsRaw);
        return new FileSummary(relativePath, summary.trim(), purpose.trim(), concepts);
    }

    private static String extractSection(String response, String label) {
        int idx = indexOfSection(response, label);
        if (idx < 0) {
            return "";
        }
        int start = response.indexOf(':', idx);
        if (start < 0) {
            return "";
        }
        start++;
        int end = response.length();
        for (String nextLabel : List.of("SUMMARY", "PURPOSE", "KEY_CONCEPTS")) {
            if (nextLabel.equalsIgnoreCase(label)) {
                continue;
            }
            int next = indexOfSection(response.substring(start), nextLabel);
            if (next >= 0) {
                end = Math.min(end, start + next);
            }
        }
        return response.substring(start, end).trim();
    }

    private static int indexOfSection(String text, String label) {
        Matcher matcher = Pattern.compile("(^|\\n)\\s*\\d+\\)?\\s*" + label + "\\b", Pattern.CASE_INSENSITIVE)
                .matcher(text);
        if (matcher.find()) {
            return matcher.start();
        }
        matcher = Pattern.compile("(^|\\n)\\s*" + label + "\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        return matcher.find() ? matcher.start() : -1;
    }

    private static List<String> parseConcepts(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] parts = raw.split("[,\\n]");
        List<String> concepts = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                concepts.add(trimmed);
            }
        }
        return List.copyOf(concepts);
    }
}
