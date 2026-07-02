package com.projectmind.adapter.ollama;

import com.projectmind.core.concurrent.ParallelExecutor;
import com.projectmind.core.domain.OllamaModelInfo;
import com.projectmind.core.port.ConfigurationPort;
import com.projectmind.core.port.OllamaClientPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static com.projectmind.adapter.ollama.OllamaApiModels.EmbeddingsRequest;
import static com.projectmind.adapter.ollama.OllamaApiModels.EmbeddingsResponse;
import static com.projectmind.adapter.ollama.OllamaApiModels.GenerateRequest;
import static com.projectmind.adapter.ollama.OllamaApiModels.GenerateResponse;
import static com.projectmind.adapter.ollama.OllamaApiModels.TagsResponse;

/**
 * HTTP client for the local Ollama API with retry, timeout, and streaming support.
 */
@Component
public class OllamaHttpClient implements OllamaClientPort {

    private static final Logger log = LoggerFactory.getLogger(OllamaHttpClient.class);

    private final ConfigurationPort config;
    private final WebClient webClient;
    private final Duration timeout;
    private final Retry retrySpec;
    private volatile String resolvedCompletionModel;
    private volatile String resolvedEmbedModel;
    private volatile TagsResponse cachedTags;

    @Autowired
    public OllamaHttpClient(ConfigurationPort config) {
        this(config, WebClient.builder().baseUrl(config.getOllamaBaseUrl()).build());
    }

    static OllamaHttpClient forTesting(ConfigurationPort config, WebClient webClient) {
        return new OllamaHttpClient(config, webClient);
    }

    private OllamaHttpClient(ConfigurationPort config, WebClient webClient) {
        this.config = config;
        this.webClient = webClient;
        this.timeout = Duration.ofSeconds(Math.max(1, config.getOllamaTimeoutSeconds()));
        this.retrySpec = Retry.backoff(Math.max(0, config.getOllamaMaxRetries()), Duration.ofMillis(300))
                .filter(OllamaHttpClient::isRetryable)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }

    @Override
    public String complete(String prompt) {
        return complete(prompt, null);
    }

    @Override
    public String complete(String prompt, String model) {
        GenerateResponse response = webClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new GenerateRequest(resolveModelForCompletion(model), prompt, false))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toOllamaException)
                .bodyToMono(GenerateResponse.class)
                .timeout(timeout)
                .retryWhen(retrySpec)
                .block();

        if (response == null || response.response() == null) {
            throw new OllamaClientException("Ollama returned an empty completion response");
        }
        return response.response().trim();
    }

    @Override
    public void completeStreaming(String prompt, Consumer<String> consumer) {
        completeStreaming(prompt, null, consumer);
    }

    @Override
    public void completeStreaming(String prompt, String model, Consumer<String> consumer) {
        webClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_NDJSON, MediaType.APPLICATION_JSON)
                .bodyValue(new GenerateRequest(resolveModelForCompletion(model), prompt, true))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toOllamaException)
                .bodyToFlux(GenerateResponse.class)
                .timeout(timeout)
                .retryWhen(retrySpec)
                .doOnNext(chunk -> {
                    if (chunk.response() != null && !chunk.response().isEmpty()) {
                        consumer.accept(chunk.response());
                    }
                })
                .blockLast();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        int concurrency = Math.max(1, config.getOllamaEmbedConcurrency());
        if (texts.size() == 1 || concurrency == 1) {
            return List.of(embedSingle(texts.get(0)));
        }

        List<Callable<float[]>> tasks = texts.stream()
                .map(text -> (Callable<float[]>) () -> embedSingle(text))
                .toList();
        return ParallelExecutor.invokeAll(tasks, concurrency);
    }

    private float[] embedSingle(String text) {
        EmbeddingsResponse response = webClient.post()
                .uri("/api/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new EmbeddingsRequest(embedModel(), text))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toOllamaException)
                .bodyToMono(EmbeddingsResponse.class)
                .timeout(timeout)
                .retryWhen(retrySpec)
                .block();

        if (response == null || response.embedding() == null || response.embedding().isEmpty()) {
            throw new OllamaClientException("Ollama returned an empty embedding response");
        }
        return toFloatArray(response.embedding());
    }

    @Override
    public List<OllamaModelInfo> listModels() {
        return loadModelTags().models().stream()
                .filter(model -> model.name() != null && !model.name().isBlank())
                .map(model -> new OllamaModelInfo(
                        model.name(),
                        model.capabilities() != null ? List.copyOf(model.capabilities()) : List.of()))
                .toList();
    }

    @Override
    public boolean isAvailable() {
        try {
            TagsResponse tags = loadInstalledModels();
            return tags != null
                    && tags.models() != null
                    && OllamaModelResolver.hasCompletionModel(tags.models());
        } catch (Exception e) {
            log.debug("Ollama not available at {}: {}", config.getOllamaBaseUrl(), e.getMessage());
            return false;
        }
    }

    boolean isEmbeddingAvailable() {
        try {
            TagsResponse tags = loadInstalledModels();
            return tags != null
                    && tags.models() != null
                    && OllamaModelResolver.hasEmbeddingModel(tags.models());
        } catch (Exception e) {
            log.debug("Ollama embeddings not available at {}: {}", config.getOllamaBaseUrl(), e.getMessage());
            return false;
        }
    }

    @Override
    public void requireReady() {
        completionModel();
    }

    @Override
    public void requireReachable() {
        if (loadModelTags().models().isEmpty()) {
            throw new OllamaClientException(
                    "No Ollama models are installed. Run: ollama pull <model>");
        }
    }

    @Override
    public String getModelName() {
        return completionModel();
    }

    private String resolveModelForCompletion(String modelOverride) {
        if (modelOverride != null && !modelOverride.isBlank()) {
            return OllamaModelResolver.requireInstalledModel(
                    loadModelTags().models(), modelOverride);
        }
        return completionModel();
    }

    private String completionModel() {
        if (resolvedCompletionModel == null) {
            synchronized (this) {
                if (resolvedCompletionModel == null) {
                    resolvedCompletionModel = OllamaModelResolver.resolveCompletionModel(
                            loadModelTags().models(), config.getOllamaModel());
                }
            }
        }
        return resolvedCompletionModel;
    }

    private String embedModel() {
        if (resolvedEmbedModel == null) {
            synchronized (this) {
                if (resolvedEmbedModel == null) {
                    resolvedEmbedModel = OllamaModelResolver.resolveEmbedModel(
                            loadModelTags().models(), config.getOllamaEmbedModel());
                }
            }
        }
        return resolvedEmbedModel;
    }

    private TagsResponse loadModelTags() {
        if (cachedTags != null) {
            return cachedTags;
        }
        synchronized (this) {
            if (cachedTags != null) {
                return cachedTags;
            }
            TagsResponse tags;
            try {
                tags = loadInstalledModels();
            } catch (Exception e) {
                throw new OllamaClientException(
                        "Cannot reach Ollama at " + config.getOllamaBaseUrl()
                                + ". Start Ollama and verify it is running.", e);
            }

            if (tags == null || tags.models() == null) {
                throw new OllamaClientException(
                        "No Ollama models are installed. Run: ollama pull <model>");
            }
            cachedTags = tags;
            return cachedTags;
        }
    }

    private TagsResponse loadInstalledModels() {
        return webClient.get()
                .uri("/api/tags")
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toOllamaException)
                .bodyToMono(TagsResponse.class)
                .timeout(Duration.ofSeconds(5))
                .block();
    }

    private Mono<? extends Throwable> toOllamaException(ClientResponse response) {
        return response.bodyToMono(OllamaApiModels.ErrorResponse.class)
                .defaultIfEmpty(new OllamaApiModels.ErrorResponse(null))
                .flatMap(error -> Mono.error(buildOllamaException(response, error)));
    }

    private OllamaClientException buildOllamaException(
            ClientResponse response,
            OllamaApiModels.ErrorResponse error) {
        String detail = error.error() != null ? error.error() : response.statusCode().toString();
        return new OllamaClientException(
                "Ollama request failed (" + response.statusCode() + "): " + detail);
    }

    private static float[] toFloatArray(List<Double> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i).floatValue();
        }
        return result;
    }

    private static boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            return ex.getStatusCode().is5xxServerError() || ex.getStatusCode().value() == 429;
        }
        return throwable instanceof java.io.IOException
                || throwable instanceof java.util.concurrent.TimeoutException;
    }
}
