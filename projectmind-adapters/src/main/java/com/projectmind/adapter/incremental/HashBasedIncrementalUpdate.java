package com.projectmind.adapter.incremental;

import com.projectmind.core.domain.FileChangeSet;
import com.projectmind.core.domain.RepositoryFile;
import com.projectmind.core.domain.RepositoryIndex;
import com.projectmind.core.port.IncrementalUpdatePort;
import com.projectmind.core.port.RepositoryScannerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Detects file changes by comparing content hashes against stored index.
 */
@Component
public class HashBasedIncrementalUpdate implements IncrementalUpdatePort {

    private static final Logger log = LoggerFactory.getLogger(HashBasedIncrementalUpdate.class);

    private final RepositoryScannerPort scanner;

    public HashBasedIncrementalUpdate(RepositoryScannerPort scanner) {
        this.scanner = scanner;
    }

    @Override
    public FileChangeSet detectChanges(Path repositoryPath, RepositoryIndex storedIndex) {
        Map<String, String> storedHashes = storedIndex.files().stream()
                .collect(Collectors.toMap(
                        f -> f.relativePath().toString(),
                        RepositoryFile::contentHash));

        Map<String, RepositoryFile> storedByPath = storedIndex.files().stream()
                .collect(Collectors.toMap(
                        f -> f.relativePath().toString(),
                        f -> f,
                        (left, right) -> right));

        List<RepositoryFile> currentFiles = scanner.scanStream(repositoryPath, storedByPath).toList();
        Map<String, RepositoryFile> currentMap = currentFiles.stream()
                .collect(Collectors.toMap(
                        f -> f.relativePath().toString(),
                        f -> f));

        List<RepositoryFile> added = new ArrayList<>();
        List<RepositoryFile> modified = new ArrayList<>();
        List<String> deleted = new ArrayList<>();

        for (RepositoryFile current : currentFiles) {
            String path = current.relativePath().toString();
            String storedHash = storedHashes.get(path);
            if (storedHash == null) {
                added.add(current);
            } else if (!storedHash.equals(current.contentHash())) {
                modified.add(current);
            }
        }

        for (String storedPath : storedHashes.keySet()) {
            if (!currentMap.containsKey(storedPath)) {
                deleted.add(storedPath);
            }
        }

        log.info("Change detection: {} added, {} modified, {} deleted",
                added.size(), modified.size(), deleted.size());

        return new FileChangeSet(added, modified, deleted);
    }
}
