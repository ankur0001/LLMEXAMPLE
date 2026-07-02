package com.projectmind.application.service;

import com.projectmind.core.concurrent.ParallelExecutor;
import com.projectmind.core.domain.FileChangeSet;
import com.projectmind.core.domain.FileSummary;
import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.RepositoryFile;
import com.projectmind.core.port.ConfigurationPort;
import com.projectmind.core.port.MemoryManagerPort;
import com.projectmind.core.port.OllamaClientPort;
import com.projectmind.core.prompt.OllamaPromptTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Re-summarizes only added or modified files via Ollama.
 */
public class RepositorySummaryGenerator {

    private static final Logger log = LoggerFactory.getLogger(RepositorySummaryGenerator.class);
    private static final Set<FileType> SUMMARIZABLE_TYPES = EnumSet.of(
            FileType.JAVA, FileType.KOTLIN, FileType.YAML, FileType.PROPERTIES,
            FileType.SQL, FileType.XML, FileType.JSON, FileType.MAVEN, FileType.GRADLE,
            FileType.MARKDOWN, FileType.KUBERNETES, FileType.DOCKER, FileType.SHELL);

    private final OllamaClientPort ollamaClient;
    private final MemoryManagerPort memoryManager;
    private final ConfigurationPort configuration;

    public RepositorySummaryGenerator(
            OllamaClientPort ollamaClient,
            MemoryManagerPort memoryManager,
            ConfigurationPort configuration) {
        this.ollamaClient = ollamaClient;
        this.memoryManager = memoryManager;
        this.configuration = configuration;
    }

    public int summarizeChanges(Path repositoryPath, FileChangeSet changes) {
        for (String deleted : changes.deleted()) {
            memoryManager.deleteSummary(repositoryPath, deleted);
        }

        List<RepositoryFile> candidates = changes.changedFiles().stream()
                .filter(file -> SUMMARIZABLE_TYPES.contains(file.fileType()))
                .toList();
        if (candidates.isEmpty()) {
            return 0;
        }

        List<Integer> counts = ParallelExecutor.invokeAll(
                candidates.stream()
                        .map(file -> (Callable<Integer>) () -> summarizeFile(repositoryPath, file))
                        .toList(),
                configuration.getSummaryConcurrency());

        int summarized = counts.stream().mapToInt(Integer::intValue).sum();
        log.info("Summarized {} changed files for {}", summarized, repositoryPath);
        return summarized;
    }

    private int summarizeFile(Path repositoryPath, RepositoryFile file) {
        try {
            String source = Files.readString(file.absolutePath());
            if (source.isBlank()) {
                return 0;
            }

            if (configuration.isCacheEnabled()) {
                String cacheKey = "summary:" + sha256(source);
                var cached = memoryManager.getCacheEntry(repositoryPath, cacheKey);
                if (cached.isPresent()) {
                    FileSummary summary = FileSummaryParser.parse(file.relativePath().toString(), cached.get());
                    memoryManager.saveSummary(repositoryPath, summary);
                    return 1;
                }
            }

            String response = ollamaClient.complete(
                    OllamaPromptTemplates.fileSummary(file.relativePath().toString(), source));
            if (configuration.isCacheEnabled()) {
                memoryManager.putCacheEntry(repositoryPath, "summary:" + sha256(source), response);
            }
            FileSummary summary = FileSummaryParser.parse(file.relativePath().toString(), response);
            memoryManager.saveSummary(repositoryPath, summary);
            return 1;
        } catch (IOException ex) {
            log.warn("Failed to read {} for summarization: {}", file.relativePath(), ex.getMessage());
        } catch (Exception ex) {
            log.warn("Failed to summarize {}: {}", file.relativePath(), ex.getMessage());
        }
        return 0;
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
