package com.projectmind.core.vector;

/**
 * Splits source text into overlapping chunks for embedding.
 */
public final class TextChunker {

    private TextChunker() {
    }

    /**
     * Chunks text by character window with line-boundary preference.
     */
    public static java.util.List<String> chunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) {
            return java.util.List.of();
        }
        if (text.length() <= chunkSize) {
            return java.util.List.of(text);
        }

        int safeOverlap = Math.max(0, Math.min(overlap, chunkSize / 2));
        java.util.List<String> chunks = new java.util.ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + chunkSize);
            if (end < text.length()) {
                int lineBreak = text.lastIndexOf('\n', end);
                if (lineBreak > start + chunkSize / 2) {
                    end = lineBreak;
                }
            }
            chunks.add(text.substring(start, end).trim());
            if (end >= text.length()) {
                break;
            }
            start = Math.max(end - safeOverlap, start + 1);
        }
        return chunks.stream().filter(chunk -> !chunk.isBlank()).toList();
    }
}
