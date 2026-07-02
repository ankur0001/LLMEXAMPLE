package com.projectmind.application.service;

import com.projectmind.core.domain.ProgressCallback;
import com.projectmind.core.domain.ProjectMetadata;
import com.projectmind.core.domain.RepositoryFile;
import com.projectmind.core.domain.RepositoryIndex;
import com.projectmind.core.domain.ScanCheckpoint;
import com.projectmind.core.domain.ScanStatistics;
import com.projectmind.core.domain.ScanStatus;
import com.projectmind.core.path.RepositoryPathResolver;
import com.projectmind.core.port.ConfigurationPort;
import com.projectmind.core.port.MemoryManagerPort;
import com.projectmind.core.port.RepositoryScannerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Shared scan orchestration: checkpoints, progress persistence, and index merging.
 */
final class ScanOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ScanOrchestrator.class);

    private ScanOrchestrator() {
    }

    static ProjectMetadata runScan(
            Path repositoryPath,
            ProgressCallback progress,
            List<RepositoryFile> existingFiles,
            RepositoryScannerPort scanner,
            MemoryManagerPort memoryManager,
            ConfigurationPort configuration) {
        Path resolvedPath = RepositoryPathResolver.resolve(repositoryPath);
        memoryManager.initializeMemory(resolvedPath);

        Instant startedAt = memoryManager.loadScanCheckpoint(resolvedPath)
                .map(ScanCheckpoint::startedAt)
                .orElse(Instant.now());

        ProjectMetadata metadata = loadOrCreateMetadata(resolvedPath, memoryManager, startedAt);
        memoryManager.saveMetadata(resolvedPath, withStatus(metadata, ScanStatus.SCANNING));

        Set<String> skipPaths = existingFiles.stream()
                .map(f -> normalizePath(f.relativePath()))
                .collect(Collectors.toUnmodifiableSet());

        Instant scanStarted = Instant.now();
        List<RepositoryFile> accumulated = new ArrayList<>(existingFiles);

        AtomicInteger checkpointBatch = new AtomicInteger();
        int checkpointInterval = Math.max(1, configuration.getScanCheckpointInterval());

        RepositoryIndex newScan = scanner.scan(
                resolvedPath,
                progress,
                skipPaths,
                batch -> {
                    if (checkpointBatch.incrementAndGet() % checkpointInterval == 0) {
                        persistProgress(resolvedPath, memoryManager, startedAt, batch, accumulated);
                    }
                });

        if (checkpointBatch.get() % checkpointInterval != 0) {
            persistProgress(resolvedPath, memoryManager, startedAt, newScan.files(), accumulated);
        }

        List<RepositoryFile> allFiles = mergeFiles(accumulated, newScan.files());
        ScanStatistics statistics = ScanStatistics.fromFiles(allFiles);
        long durationMs = newScan.scanDurationMs();

        RepositoryIndex finalIndex = new RepositoryIndex(
                resolvedPath,
                Instant.now(),
                allFiles.size(),
                allFiles,
                statistics,
                durationMs);

        memoryManager.saveIndex(resolvedPath, finalIndex);
        memoryManager.clearScanProgress(resolvedPath);
        memoryManager.clearScanCheckpoint(resolvedPath);

        String canonicalPath = RepositoryPathResolver.toStorageKey(resolvedPath);
        ProjectMetadata completed = new ProjectMetadata(
                metadata.name(),
                canonicalPath,
                ScanStatus.INDEXED,
                metadata.firstScannedAt(),
                Instant.now(),
                Instant.now(),
                finalIndex.totalFiles(),
                finalIndex.totalFiles(),
                metadata.ollamaModel(),
                metadata.properties());

        memoryManager.saveMetadata(resolvedPath, completed);
        log.info("Scan complete: {} files indexed in {} ms", finalIndex.totalFiles(), durationMs);
        return completed;
    }

    static List<RepositoryFile> mergeFiles(List<RepositoryFile> existing, List<RepositoryFile> additional) {
        Map<String, RepositoryFile> merged = new LinkedHashMap<>();
        for (RepositoryFile file : existing) {
            merged.put(normalizePath(file.relativePath()), file);
        }
        for (RepositoryFile file : additional) {
            merged.put(normalizePath(file.relativePath()), file);
        }
        return List.copyOf(merged.values());
    }

    private static void persistProgress(
            Path repositoryPath,
            MemoryManagerPort memoryManager,
            Instant startedAt,
            List<RepositoryFile> batch,
            List<RepositoryFile> accumulated) {
        List<RepositoryFile> merged = mergeFiles(accumulated, batch);
        accumulated.clear();
        accumulated.addAll(merged);

        RepositoryIndex progressIndex = new RepositoryIndex(
                repositoryPath,
                Instant.now(),
                merged.size(),
                merged,
                ScanStatistics.fromFiles(merged),
                0);

        memoryManager.saveScanProgress(repositoryPath, progressIndex);
        memoryManager.saveScanCheckpoint(repositoryPath, new ScanCheckpoint(
                startedAt,
                Instant.now(),
                merged.size(),
                merged.size(),
                merged.isEmpty() ? "" : normalizePath(merged.get(merged.size() - 1).relativePath()),
                false));
    }

    private static ProjectMetadata loadOrCreateMetadata(
            Path repositoryPath,
            MemoryManagerPort memoryManager,
            Instant startedAt) {
        return memoryManager.loadMetadata(repositoryPath)
                .map(existing -> new ProjectMetadata(
                        existing.name(),
                        RepositoryPathResolver.toStorageKey(repositoryPath),
                        ScanStatus.SCANNING,
                        existing.firstScannedAt() != null ? existing.firstScannedAt() : startedAt,
                        Instant.now(),
                        existing.lastUpdatedAt(),
                        existing.totalFiles(),
                        existing.indexedFiles(),
                        existing.ollamaModel(),
                        existing.properties()))
                .orElseGet(() -> new ProjectMetadata(
                        repositoryPath.getFileName().toString(),
                        RepositoryPathResolver.toStorageKey(repositoryPath),
                        ScanStatus.SCANNING,
                        startedAt,
                        Instant.now(),
                        null,
                        0,
                        0,
                        "qwen2.5-coder:14b-instruct",
                        new HashMap<>()));
    }

    private static ProjectMetadata withStatus(ProjectMetadata metadata, ScanStatus status) {
        return new ProjectMetadata(
                metadata.name(),
                metadata.repositoryPath(),
                status,
                metadata.firstScannedAt(),
                metadata.lastScannedAt(),
                metadata.lastUpdatedAt(),
                metadata.totalFiles(),
                metadata.indexedFiles(),
                metadata.ollamaModel(),
                metadata.properties());
    }

    static String normalizePath(Path relativePath) {
        return relativePath.toString().replace('\\', '/');
    }
}
