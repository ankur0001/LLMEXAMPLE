package com.projectmind.adapter.ollama;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaModelResolverTest {

    @Test
    void prefersConfiguredCompletionModelWhenInstalled() {
        var models = List.of(
                model("phi3:latest", "completion"),
                model("qwen2.5-coder:14b-instruct", "completion"));

        assertThat(OllamaModelResolver.resolveCompletionModel(models, "qwen2.5-coder:14b-instruct"))
                .isEqualTo("qwen2.5-coder:14b-instruct");
    }

    @Test
    void fallsBackToInstalledCompletionModelWhenPreferredMissing() {
        var models = List.of(
                model("phi3:latest", "completion"),
                model("nomic-embed-text:latest", "embedding"));

        assertThat(OllamaModelResolver.resolveCompletionModel(models, "qwen2.5-coder:14b-instruct"))
                .isEqualTo("phi3:latest");
    }

    @Test
    void autoSelectsFirstCompletionModelWhenPreferenceIsAuto() {
        var models = List.of(
                model("gemma4:e2b", "completion"),
                model("phi3:latest", "completion"));

        assertThat(OllamaModelResolver.resolveCompletionModel(models, "auto"))
                .isEqualTo("gemma4:e2b");
    }

    @Test
    void prefersConfiguredEmbedModelWhenInstalled() {
        var models = List.of(
                model("phi3:latest", "completion"),
                model("nomic-embed-text:latest", "embedding"));

        assertThat(OllamaModelResolver.resolveEmbedModel(models, "nomic-embed-text"))
                .isEqualTo("nomic-embed-text:latest");
    }

    @Test
    void fallsBackToInstalledEmbedModelWhenPreferredMissing() {
        var models = List.of(
                model("phi3:latest", "completion"),
                model("mxbai-embed-large:latest", "embedding"));

        assertThat(OllamaModelResolver.resolveEmbedModel(models, "nomic-embed-text"))
                .isEqualTo("mxbai-embed-large:latest");
    }

    @Test
    void throwsWhenNoCompletionModelsInstalled() {
        var models = List.of(model("nomic-embed-text:latest", "embedding"));

        assertThatThrownBy(() -> OllamaModelResolver.resolveCompletionModel(models, "auto"))
                .isInstanceOf(OllamaClientException.class)
                .hasMessageContaining("No Ollama completion model");
    }

    @Test
    void requireInstalledModelMatchesPartialNames() {
        var models = List.of(
                model("phi3:latest", "completion"),
                model("nomic-embed-text:latest", "embedding"));

        assertThat(OllamaModelResolver.requireInstalledModel(models, "phi3"))
                .isEqualTo("phi3:latest");
    }

    private static OllamaApiModels.ModelInfo model(String name, String capability) {
        return new OllamaApiModels.ModelInfo(name, List.of(capability));
    }
}
