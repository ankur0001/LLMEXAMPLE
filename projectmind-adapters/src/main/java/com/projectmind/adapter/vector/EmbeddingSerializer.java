package com.projectmind.adapter.vector;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Serializes embedding vectors to/from cache strings.
 */
final class EmbeddingSerializer {

    private EmbeddingSerializer() {
    }

    static String serialize(float[] embedding) {
        StringBuilder sb = new StringBuilder(embedding.length * 8);
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        return sb.toString();
    }

    static float[] deserialize(String value) {
        if (value == null || value.isBlank()) {
            return new float[0];
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(Float::parseFloat)
                .collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
                    float[] result = new float[list.size()];
                    for (int i = 0; i < list.size(); i++) {
                        result[i] = list.get(i);
                    }
                    return result;
                }));
    }
}
