package com.projectmind.core.vector;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    @Test
    void returnsSingleChunkForSmallText() {
        assertThat(TextChunker.chunk("hello", 100, 10)).containsExactly("hello");
    }

    @Test
    void splitsLargeTextIntoOverlappingChunks() {
        String text = "a".repeat(100) + "\n" + "b".repeat(100);
        var chunks = TextChunker.chunk(text, 120, 20);

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks.get(0).length()).isLessThanOrEqualTo(120);
    }
}
