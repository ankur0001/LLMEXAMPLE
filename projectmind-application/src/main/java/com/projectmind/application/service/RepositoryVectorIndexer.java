package com.projectmind.application.service;

import com.projectmind.core.domain.FileChangeSet;
import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.RepositoryFile;
import com.projectmind.core.domain.RepositoryIndex;
import com.projectmind.core.domain.VectorDocument;
import com.projectmind.core.port.ConfigurationPort;
import com.projectmind.core.port.MemoryManagerPort;
import com.projectmind.core.port.VectorIndexPort;
import com.projectmind.core.vector.TextChunker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Chunks repository files, embeds them, and stores vectors for semantic search.
 */
public class RepositoryVectorIndexer {

    private static final Logger log = LoggerFactory.getLogger(RepositoryVectorIndexer.class);
    private static final Set<FileType> INDEXABLE_TYPES = EnumSet.of(
            FileType.JAVA, FileType.KOTLIN, FileType.YAML, FileType.PROPERTIES,
            FileType.SQL, FileType.XML, FileType.JSON, FileType.MAVEN, FileType.GRADLE,
            FileType.MARKDOWN, FileType.KUBERNETES, FileType.DOCKER, FileType.SHELL);

    private final VectorIndexPort vectorIndex;
    private final MemoryManagerPort memoryManager;
    private final int chunkSize;
    private final int chunkOverlap;
    private final int batchSize;

    public RepositoryVectorIndexer(
            VectorIndexPort vectorIndex,
            MemoryManagerPort memoryManager,
            ConfigurationPort configuration) {
        this.vectorIndex = vectorIndex;
        this.memoryManager = memoryManager;
        this.chunkSize = configuration.getVectorChunkSize();
        this.chunkOverlap = configuration.getVectorChunkOverlap();
        this.batchSize = configuration.getVectorBatchSize();
    }

    public long indexRepository(Path repositoryPath) {
        RepositoryIndex index = memoryManager.loadIndex(repositoryPath).orElse(null);
        if (index == null || index.files().isEmpty()) {
            return 0;
        }

        List<VectorDocument> batch = new ArrayList<>();
        long totalChunks = 0;
        for (RepositoryFile file : index.files()) {
            if (!INDEXABLE_TYPES.contains(file.fileType())) {
                continue;
            }
            try {
                String content = Files.readString(file.absolutePath());
                if (content.isBlank()) {
                    continue;
                }
                List<String> chunks = TextChunker.chunk(content, chunkSize, chunkOverlap);
                String summary = memoryManager.loadSummary(repositoryPath, file.relativePath().toString())
                        .map(value -> value.summary())
                        .orElse("");

                for (int i = 0; i < chunks.size(); i++) {
                    String relativePath = file.relativePath().toString();
                    batch.add(new VectorDocument(
                            documentId(relativePath, i),
                            relativePath,
                            chunks.get(i),
                            summary,
                            file.fileType()));
                    if (batch.size() >= batchSize) {
                        vectorIndex.index(repositoryPath, List.copyOf(batch));
                        totalChunks += batch.size();
                        batch.clear();
                    }
                }
            } catch (IOException ex) {
                log.warn("Failed to vector-index {}: {}", file.relativePath(), ex.getMessage());
            }
        }

        if (!batch.isEmpty()) {
            vectorIndex.index(repositoryPath, batch);
            totalChunks += batch.size();
        }

        log.info("Vector index updated: {} chunks indexed for {}", totalChunks, repositoryPath);
        return totalChunks;
    }

    /**
     * Removes stale vectors and re-embeds only added or modified files.
     */
    public long updateChangedFiles(Path repositoryPath, FileChangeSet changes) {
        if (!changes.hasChanges()) {
            return 0;
        }

        List<String> pathsToRemove = new ArrayList<>(changes.deleted());
        for (RepositoryFile file : changes.modified()) {
            pathsToRemove.add(file.relativePath().toString());
        }
        if (!pathsToRemove.isEmpty()) {
            vectorIndex.remove(repositoryPath, pathsToRemove);
        }

        List<VectorDocument> batch = new ArrayList<>();
        long totalChunks = 0;
        for (RepositoryFile file : changes.changedFiles()) {
            if (!INDEXABLE_TYPES.contains(file.fileType())) {
                continue;
            }
            totalChunks += indexFile(repositoryPath, file, batch);
        }
        if (!batch.isEmpty()) {
            vectorIndex.index(repositoryPath, batch);
            totalChunks += batch.size();
        }

        log.info("Incremental vector update: {} chunks indexed for {}", totalChunks, repositoryPath);
        return totalChunks;
    }

    private long indexFile(Path repositoryPath, RepositoryFile file, List<VectorDocument> batch) {
        try {
            String content = Files.readString(file.absolutePath());
            if (content.isBlank()) {
                return 0;
            }
            List<String> chunks = TextChunker.chunk(content, chunkSize, chunkOverlap);
            String summary = memoryManager.loadSummary(repositoryPath, file.relativePath().toString())
                    .map(value -> value.summary())
                    .orElse("");

            long indexed = 0;
            for (int i = 0; i < chunks.size(); i++) {
                String relativePath = file.relativePath().toString();
                batch.add(new VectorDocument(
                        documentId(relativePath, i),
                        relativePath,
                        chunks.get(i),
                        summary,
                        file.fileType()));
                if (batch.size() >= batchSize) {
                    vectorIndex.index(repositoryPath, List.copyOf(batch));
                    indexed += batch.size();
                    batch.clear();
                }
            }
            return indexed;
        } catch (IOException ex) {
            log.warn("Failed to vector-index {}: {}", file.relativePath(), ex.getMessage());
            return 0;
        }
    }

    private static String documentId(String relativePath, int chunkIndex) {
        return relativePath.replace('/', '_').replace('\\', '_') + "#" + chunkIndex;
    }
}
