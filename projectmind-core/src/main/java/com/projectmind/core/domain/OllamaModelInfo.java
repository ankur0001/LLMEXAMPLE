package com.projectmind.core.domain;

import java.util.List;

/**
 * An Ollama model reported by the local {@code /api/tags} endpoint.
 */
public record OllamaModelInfo(String name, List<String> capabilities) {
}
