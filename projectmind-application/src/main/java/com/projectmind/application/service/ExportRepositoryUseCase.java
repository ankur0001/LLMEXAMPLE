package com.projectmind.application.service;

import com.projectmind.core.port.MemoryManagerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Exports persisted repository memory to a target directory.
 */
public class ExportRepositoryUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExportRepositoryUseCase.class);

    private final MemoryManagerPort memoryManager;

    public ExportRepositoryUseCase(MemoryManagerPort memoryManager) {
        this.memoryManager = memoryManager;
    }

    /**
     * Copies {@code .ai-memory} into {@code outputDirectory/.ai-memory}.
     */
    public Path execute(Path repositoryPath, Path outputDirectory) {
        Path memoryPath = memoryManager.memoryPath(repositoryPath);
        if (!Files.exists(memoryPath)) {
            throw new IllegalStateException("No memory found for repository: " + repositoryPath);
        }

        Path exportTarget = outputDirectory.resolve(".ai-memory").toAbsolutePath().normalize();
        try {
            Files.createDirectories(outputDirectory);
            if (Files.exists(exportTarget)) {
                deleteRecursively(exportTarget);
            }
            copyRecursively(memoryPath, exportTarget);
            log.info("Exported memory from {} to {}", repositoryPath, exportTarget);
            return exportTarget;
        } catch (IOException ex) {
            throw new RuntimeException("Failed to export repository memory", ex);
        }
    }

    private static void copyRecursively(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Files.createDirectories(target.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(file);
                Files.copy(file, target.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
