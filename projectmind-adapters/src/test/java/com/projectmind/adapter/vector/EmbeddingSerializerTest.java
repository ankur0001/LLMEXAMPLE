package com.projectmind.adapter.vector;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingSerializerTest {

    @Test
    void roundTripsEmbeddingVectors() {
        float[] original = new float[] {0.1f, -0.2f, 0.3f, 1.5f};
        String encoded = EmbeddingSerializer.serialize(original);
        float[] decoded = EmbeddingSerializer.deserialize(encoded);
        assertThat(decoded).containsExactly(original);
    }
}
