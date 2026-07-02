package com.projectmind.adapter.scanner;

import com.projectmind.core.concurrent.ParallelExecutor;
import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.ProgressCallback;
import com.projectmind.core.domain.RepositoryFile;
import com.projectmind.core.domain.RepositoryIndex;
import com.projectmind.core.domain.ScanStatistics;
import com.projectmind.core.port.ConfigurationPort;
import com.projectmind.core.port.RepositoryScannerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Recursively scans repository filesystems with directory pruning, batching, and progress reporting.
 */
@Component
public class FileSystemRepositoryScanner implements RepositoryScannerPort {

    private static final Logger log = LoggerFactory.getLogger(FileSystemRepositoryScanner.class);

    private final ConfigurationPort config;
    private final FileTypeDetector fileTypeDetector;

    @Autowired
    public FileSystemRepositoryScanner(ConfigurationPort config) {
        this(config, new FileTypeDetector());
    }

    FileSystemRepositoryScanner(ConfigurationPort config, FileTypeDetector fileTypeDetector) {
        this.config = config;
        this.fileTypeDetector = fileTypeDetector;
    }

    @Override
    public RepositoryIndex scan(Path repositoryPath, ProgressCallback progress) {
        return scan(repositoryPath, progress, Set.of());
    }

    @Override
    public RepositoryIndex scan(Path repositoryPath, ProgressCallback progress, Set<String> skipRelativePaths) {
        return scan(repositoryPath, progress, skipRelativePaths, null);
    }

    @Override
    public RepositoryIndex scan(
            Path repositoryPath,
            ProgressCallback progress,
            Set<String> skipRelativePaths,
            Consumer<List<RepositoryFile>> batchSnapshot) {
        validateRepository(repositoryPath);
        long startNanos = System.nanoTime();

        Set<String> skipPaths = normalizeSkipPaths(skipRelativePaths);
        Set<String> skipDirs = Set.copyOf(config.getSkipDirectories());
        log.info("Scanning repository: {} (skip dirs: {}, resume skip: {})",
                repositoryPath, skipDirs, skipPaths.size());

        List<Path> discovered = discoverFiles(repositoryPath, skipDirs);
        progress.onProgress("discover", discovered.size(), discovered.size(),
                "Discovered " + discovered.size() + " files");

        List<RepositoryFile> scannedFiles = processFiles(
                repositoryPath, discovered, skipPaths, progress, batchSnapshot);

        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        ScanStatistics statistics = ScanStatistics.fromFiles(scannedFiles);

        progress.onProgress("scan", discovered.size(), discovered.size(), "Scan complete");
        log.info("Scan finished: {} new files in {} ms", scannedFiles.size(), durationMs);

        return new RepositoryIndex(
                repositoryPath,
                Instant.now(),
                scannedFiles.size(),
                scannedFiles,
                statistics,
                durationMs);
    }

    @Override
    public Stream<RepositoryFile> scanStream(Path repositoryPath) {
        return scanStream(repositoryPath, Map.of());
    }

    @Override
    public Stream<RepositoryFile> scanStream(Path repositoryPath, Map<String, RepositoryFile> storedByPath) {
        validateRepository(repositoryPath);
        Set<String> skipDirs = Set.copyOf(config.getSkipDirectories());
        List<Path> discovered = discoverFiles(repositoryPath, skipDirs);
        return discovered.stream().map(path -> toRepositoryFile(path, repositoryPath, storedByPath));
    }

    /**
     * Discovers file paths without computing content hashes (fast first pass).
     */
    List<Path> discoverFiles(Path repositoryPath, Set<String> skipDirs) {
        List<Path> files = new ArrayList<>();
        try {
            Files.walkFileTree(repositoryPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (dir.equals(repositoryPath)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (shouldSkipDirectory(dir, repositoryPath, skipDirs)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!isInSkippedDirectory(file, repositoryPath, skipDirs)) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to discover files in: " + repositoryPath, e);
        }
        return files;
    }

    List<RepositoryFile> processFiles(
            Path repositoryPath,
            List<Path> discovered,
            Set<String> skipPaths,
            ProgressCallback progress,
            Consumer<List<RepositoryFile>> batchSnapshot) {
        int batchSize = Math.max(1, config.getScanBatchSize());
        int total = discovered.size();
        List<RepositoryFile> results = new ArrayList<>();
        int processed = 0;

        List<Integer> workIndexes = new ArrayList<>();
        for (int i = 0; i < discovered.size(); i++) {
            String relative = normalizeRelativePath(repositoryPath.relativize(discovered.get(i)));
            if (!skipPaths.contains(relative)) {
                workIndexes.add(i);
            }
        }

        List<RepositoryFile> hashed = ParallelExecutor.invokeAll(
                workIndexes.stream()
                        .map(index -> (Callable<RepositoryFile>) () ->
                                toRepositoryFile(discovered.get(index), repositoryPath, Map.of()))
                        .toList(),
                config.getScanHashConcurrency());

        int hashCursor = 0;
        for (Path absolutePath : discovered) {
            String relative = normalizeRelativePath(repositoryPath.relativize(absolutePath));
            processed++;

            if (skipPaths.contains(relative)) {
                reportProgress(progress, processed, total, relative);
                continue;
            }

            results.add(hashed.get(hashCursor++));

            if (processed % batchSize == 0 || processed == total) {
                reportProgress(progress, processed, total, relative);
                if (batchSnapshot != null && !results.isEmpty()) {
                    batchSnapshot.accept(List.copyOf(results));
                }
            }
        }
        return results;
    }

    RepositoryFile toRepositoryFile(Path absolutePath, Path repositoryRoot) {
        return toRepositoryFile(absolutePath, repositoryRoot, Map.of());
    }

    RepositoryFile toRepositoryFile(
            Path absolutePath,
            Path repositoryRoot,
            Map<String, RepositoryFile> storedByPath) {
        Path relativePath = repositoryRoot.relativize(absolutePath);
        String relative = normalizeRelativePath(relativePath);
        FileType fileType = fileTypeDetector.detect(absolutePath, repositoryRoot);
        long size;
        Instant lastModified;
        try {
            size = Files.size(absolutePath);
            lastModified = Files.getLastModifiedTime(absolutePath).toInstant();
        } catch (IOException e) {
            size = 0;
            lastModified = Instant.now();
        }

        RepositoryFile stored = storedByPath.get(relative);
        if (stored != null
                && stored.sizeBytes() == size
                && stored.lastModified().equals(lastModified)
                && stored.contentHash() != null
                && !stored.contentHash().isBlank()
                && !"unknown".equals(stored.contentHash())) {
            return new RepositoryFile(relativePath, absolutePath, fileType, stored.contentHash(), size, lastModified);
        }

        String hash = computeHash(absolutePath);
        return new RepositoryFile(relativePath, absolutePath, fileType, hash, size, lastModified);
    }

    private void validateRepository(Path repositoryPath) {
        if (!Files.isDirectory(repositoryPath)) {
            throw new IllegalArgumentException("Repository path is not a directory: " + repositoryPath);
        }
    }

    private void reportProgress(ProgressCallback progress, int current, int total, String relative) {
        progress.onProgress("scan", current, total, relative);
    }

    private boolean shouldSkipDirectory(Path dir, Path root, Set<String> skipDirs) {
        Path relative = root.relativize(dir);
        if (relative.getNameCount() == 0) {
            return false;
        }
        return skipDirs.contains(relative.getName(relative.getNameCount() - 1).toString());
    }

    private boolean isInSkippedDirectory(Path file, Path root, Set<String> skipDirs) {
        Path relative = root.relativize(file);
        for (int i = 0; i < relative.getNameCount() - 1; i++) {
            if (skipDirs.contains(relative.getName(i).toString())) {
                return true;
            }
        }
        return false;
    }

    static Set<String> normalizeSkipPaths(Set<String> skipRelativePaths) {
        return skipRelativePaths.stream()
                .map(path -> path.replace('\\', '/'))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    static String normalizeRelativePath(Path relativePath) {
        return relativePath.toString().replace('\\', '/');
    }

    private String computeHash(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            log.warn("Could not hash file: {}", path);
            return "unknown";
        }
    }
}
