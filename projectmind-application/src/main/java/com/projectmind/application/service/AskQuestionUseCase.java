package com.projectmind.application.service;

import com.projectmind.core.prompt.OllamaPromptTemplates;
import com.projectmind.core.domain.AskResponse;
import com.projectmind.core.domain.SearchResult;
import com.projectmind.core.port.MemoryManagerPort;
import com.projectmind.core.port.OllamaClientPort;
import com.projectmind.core.port.VectorIndexPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrates question-answering using vector search and Ollama.
 */
public class AskQuestionUseCase {

    private static final Logger log = LoggerFactory.getLogger(AskQuestionUseCase.class);
    private static final int DEFAULT_TOP_K = 5;

    private final VectorIndexPort vectorIndex;
    private final OllamaClientPort ollamaClient;
    private final MemoryManagerPort memoryManager;

    public AskQuestionUseCase(
            VectorIndexPort vectorIndex,
            OllamaClientPort ollamaClient,
            MemoryManagerPort memoryManager) {
        this.vectorIndex = vectorIndex;
        this.ollamaClient = ollamaClient;
        this.memoryManager = memoryManager;
    }

    /**
     * Answers a question about the repository using stored knowledge and source code.
     */
    public AskResponse execute(Path repositoryPath, String question) {
        return execute(repositoryPath, question, null, null);
    }

    /**
     * Answers a question using an optional explicit Ollama model.
     */
    public AskResponse execute(Path repositoryPath, String question, String model) {
        return execute(repositoryPath, question, model, null);
    }

    /**
     * Answers a question with optional model override and streaming output.
     */
    public AskResponse execute(
            Path repositoryPath,
            String question,
            String model,
            java.util.function.Consumer<String> streamConsumer) {
        log.info("Processing question for {}: {}", repositoryPath, question);

        if (model != null && !model.isBlank()) {
            ollamaClient.requireReachable();
        } else {
            ollamaClient.requireReady();
        }

        memoryManager.loadMetadata(repositoryPath)
                .orElseThrow(() -> new IllegalStateException(
                        "Repository not scanned. Run 'projectmind scan' first."));

        List<SearchResult> results = vectorIndex.search(repositoryPath, question, DEFAULT_TOP_K);
        List<String> sourceFiles = results.stream()
                .map(SearchResult::relativePath)
                .collect(Collectors.toList());

        String context = buildContext(repositoryPath, results);
        String prompt = OllamaPromptTemplates.questionAnswer(question, context);
        String answer;
        if (streamConsumer != null) {
            var buffer = new StringBuilder();
            ollamaClient.completeStreaming(prompt, model, chunk -> {
                buffer.append(chunk);
                streamConsumer.accept(chunk);
            });
            answer = buffer.toString();
        } else {
            answer = ollamaClient.complete(prompt, model);
        }

        String modelUsed = (model != null && !model.isBlank())
                ? model
                : ollamaClient.getModelName();
        return new AskResponse(question, answer, sourceFiles, modelUsed);
    }

    private String buildContext(Path repositoryPath, List<SearchResult> results) {
        var sb = new StringBuilder();
        for (SearchResult result : results) {
            sb.append("--- File: ").append(result.relativePath()).append(" ---\n");
            memoryManager.loadSummary(repositoryPath, result.relativePath())
                    .ifPresent(summary -> sb.append("Summary: ").append(summary.summary()).append("\n"));
            try {
                Path filePath = repositoryPath.resolve(result.relativePath());
                if (Files.exists(filePath)) {
                    String content = Files.readString(filePath);
                    if (content.length() > 4000) {
                        content = content.substring(0, 4000) + "\n... (truncated)";
                    }
                    sb.append(content).append("\n\n");
                }
            } catch (Exception e) {
                log.warn("Could not read source file: {}", result.relativePath());
            }
        }
        return sb.toString();
    }
}
