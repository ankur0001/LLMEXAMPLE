package com.projectmind.core.port;

import com.projectmind.core.domain.OllamaModelInfo;

import java.util.List;

/**
 * Port for communicating with a locally running Ollama instance.
 */
public interface OllamaClientPort {

    /**
     * Generates a text completion from the given prompt using the default or configured model.
     */
    String complete(String prompt);

    /**
     * Generates a text completion using an explicit model name, or the default when {@code model} is blank.
     */
    String complete(String prompt, String model);

    /**
     * Generates a text completion with streaming output.
     */
    void completeStreaming(String prompt, java.util.function.Consumer<String> consumer);

    /**
     * Generates a text completion with streaming output and an optional explicit model.
     */
    void completeStreaming(String prompt, String model, java.util.function.Consumer<String> consumer);

    /**
     * Generates embeddings for the given texts.
     */
    List<float[]> embed(List<String> texts);

    /**
     * Lists all models installed in the local Ollama instance.
     */
    List<OllamaModelInfo> listModels();

    /**
     * Returns true if Ollama is reachable and at least one completion model is available.
     */
    boolean isAvailable();

    /**
     * Verifies Ollama is reachable and the default completion model can be resolved.
     */
    void requireReady();

    /**
     * Verifies Ollama is reachable and at least one model is installed.
     */
    void requireReachable();

    /**
     * Returns the name of the currently resolved default completion model.
     */
    String getModelName();
}
