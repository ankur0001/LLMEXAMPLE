package com.projectmind.adapter.ollama;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Selects Ollama models from locally installed tags, preferring configured names when present.
 */
final class OllamaModelResolver {

    private static final Logger log = LoggerFactory.getLogger(OllamaModelResolver.class);

    private OllamaModelResolver() {
    }

    static String resolveCompletionModel(List<OllamaApiModels.ModelInfo> models, String preferred) {
        requireModels(models);
        Optional<String> preferredMatch = findPreferredModel(models, preferred, ModelCapability.COMPLETION);
        if (preferredMatch.isPresent()) {
            return preferredMatch.get();
        }

        String selected = firstModelForCapability(models, ModelCapability.COMPLETION)
                .orElseThrow(() -> new OllamaClientException(
                        "No Ollama completion model is installed. Run: ollama pull <model>"));

        if (isExplicitPreference(preferred)) {
            log.info("Configured Ollama model '{}' not found; using '{}'", preferred, selected);
        } else {
            log.info("Using Ollama completion model '{}'", selected);
        }
        return selected;
    }

    static String resolveEmbedModel(List<OllamaApiModels.ModelInfo> models, String preferred) {
        requireModels(models);
        Optional<String> preferredMatch = findPreferredModel(models, preferred, ModelCapability.EMBEDDING);
        if (preferredMatch.isPresent()) {
            return preferredMatch.get();
        }

        String selected = firstModelForCapability(models, ModelCapability.EMBEDDING)
                .orElseThrow(() -> new OllamaClientException(
                        "No Ollama embedding model is installed. Run: ollama pull nomic-embed-text"));

        if (isExplicitPreference(preferred)) {
            log.info("Configured Ollama embed model '{}' not found; using '{}'", preferred, selected);
        } else {
            log.info("Using Ollama embedding model '{}'", selected);
        }
        return selected;
    }

    static boolean hasCompletionModel(List<OllamaApiModels.ModelInfo> models) {
        return models != null && firstModelForCapability(models, ModelCapability.COMPLETION).isPresent();
    }

    static boolean hasEmbeddingModel(List<OllamaApiModels.ModelInfo> models) {
        return models != null && firstModelForCapability(models, ModelCapability.EMBEDDING).isPresent();
    }

    private static void requireModels(List<OllamaApiModels.ModelInfo> models) {
        if (models == null || models.isEmpty()) {
            throw new OllamaClientException(
                    "No Ollama models are installed. Run: ollama pull <model>");
        }
    }

    private static Optional<String> findPreferredModel(
            List<OllamaApiModels.ModelInfo> models,
            String preferred,
            ModelCapability capability) {
        if (!isExplicitPreference(preferred)) {
            return Optional.empty();
        }
        return models.stream()
                .filter(model -> matchesName(model.name(), preferred))
                .filter(model -> supportsCapability(model, capability))
                .map(OllamaApiModels.ModelInfo::name)
                .findFirst();
    }

    private static Optional<String> firstModelForCapability(
            List<OllamaApiModels.ModelInfo> models,
            ModelCapability capability) {
        return models.stream()
                .filter(model -> supportsCapability(model, capability))
                .map(OllamaApiModels.ModelInfo::name)
                .findFirst();
    }

    private static boolean supportsCapability(OllamaApiModels.ModelInfo model, ModelCapability capability) {
        List<String> capabilities = model.capabilities();
        if (capabilities != null && !capabilities.isEmpty()) {
            return capabilities.contains(capability.apiName());
        }
        return switch (capability) {
            case COMPLETION -> !looksLikeEmbeddingModel(model.name());
            case EMBEDDING -> looksLikeEmbeddingModel(model.name());
        };
    }

    private static boolean looksLikeEmbeddingModel(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("embed");
    }

    static boolean matchesName(String installedName, String configuredName) {
        return installedName.equals(configuredName)
                || installedName.startsWith(configuredName + ":")
                || configuredName.startsWith(installedName + ":");
    }

    static String requireInstalledModel(List<OllamaApiModels.ModelInfo> models, String requested) {
        requireModels(models);
        return models.stream()
                .map(OllamaApiModels.ModelInfo::name)
                .filter(name -> matchesName(name, requested))
                .findFirst()
                .orElseThrow(() -> new OllamaClientException(
                        "Ollama model '" + requested + "' is not installed. "
                                + "Run: ollama pull " + requested));
    }

    private static boolean isExplicitPreference(String preferred) {
        return preferred != null && !preferred.isBlank() && !"auto".equalsIgnoreCase(preferred);
    }

    private enum ModelCapability {
        COMPLETION("completion"),
        EMBEDDING("embedding");

        private final String apiName;

        ModelCapability(String apiName) {
            this.apiName = apiName;
        }

        String apiName() {
            return apiName;
        }
    }
}
