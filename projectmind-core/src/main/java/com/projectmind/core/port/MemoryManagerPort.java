package com.projectmind.core.port;

import com.projectmind.core.domain.DocSection;
import com.projectmind.core.domain.ApiSummary;
import com.projectmind.core.domain.ClassSummary;
import com.projectmind.core.domain.FileSummary;
import com.projectmind.core.domain.HistorySnapshot;
import com.projectmind.core.domain.KnowledgeGraph;
import com.projectmind.core.domain.MemoryOverview;
import com.projectmind.core.domain.PackageSummary;
import com.projectmind.core.domain.ProjectMetadata;
import com.projectmind.core.domain.RepositoryIndex;
import com.projectmind.core.domain.ScanCheckpoint;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Port for managing persistent project memory on disk.
 */
public interface MemoryManagerPort {

    String MEMORY_DIR = ".ai-memory";
    String SCAN_PROGRESS_JSON = "scan_progress.json";
    String SCAN_CHECKPOINT_JSON = "scan_checkpoint.json";

    /**
     * Initializes the .ai-memory directory structure for a repository.
     */
    Path initializeMemory(Path repositoryPath);

    /**
     * Loads project metadata from disk.
     */
    Optional<ProjectMetadata> loadMetadata(Path repositoryPath);

    /**
     * Persists project metadata to disk.
     */
    void saveMetadata(Path repositoryPath, ProjectMetadata metadata);

    /**
     * Loads the repository file index.
     */
    Optional<RepositoryIndex> loadIndex(Path repositoryPath);

    /**
     * Persists the repository file index.
     */
    void saveIndex(Path repositoryPath, RepositoryIndex index);

    /**
     * Loads partial scan progress for resumable scans.
     */
    Optional<RepositoryIndex> loadScanProgress(Path repositoryPath);

    /**
     * Persists partial scan progress during an in-flight scan.
     */
    void saveScanProgress(Path repositoryPath, RepositoryIndex progress);

    /**
     * Removes partial scan progress after a successful scan.
     */
    void clearScanProgress(Path repositoryPath);

    /**
     * Loads scan checkpoint metadata.
     */
    Optional<ScanCheckpoint> loadScanCheckpoint(Path repositoryPath);

    /**
     * Persists scan checkpoint metadata.
     */
    void saveScanCheckpoint(Path repositoryPath, ScanCheckpoint checkpoint);

    /**
     * Removes scan checkpoint after a successful scan.
     */
    void clearScanCheckpoint(Path repositoryPath);

    /**
     * Loads the knowledge graph from disk.
     */
    Optional<KnowledgeGraph> loadGraph(Path repositoryPath);

    /**
     * Persists the knowledge graph to disk.
     */
    void saveGraph(Path repositoryPath, KnowledgeGraph graph);

    /**
     * Saves a file summary to disk.
     */
    void saveSummary(Path repositoryPath, FileSummary summary);

    /**
     * Loads a file summary from disk.
     */
    Optional<FileSummary> loadSummary(Path repositoryPath, String relativePath);

    /**
     * Removes a persisted file summary.
     */
    void deleteSummary(Path repositoryPath, String relativePath);

    /**
     * Persists generated documentation sections for incremental regeneration.
     */
    void saveDocumentationSections(Path repositoryPath, List<DocSection> sections);

    /**
     * Loads previously generated documentation sections.
     */
    Optional<List<DocSection>> loadDocumentationSections(Path repositoryPath);

    /**
     * Saves a package summary to disk.
     */
    void savePackageSummary(Path repositoryPath, PackageSummary summary);

    /**
     * Loads a package summary from disk.
     */
    Optional<PackageSummary> loadPackageSummary(Path repositoryPath, String packageName);

    /**
     * Saves a class summary to disk.
     */
    void saveClassSummary(Path repositoryPath, ClassSummary summary);

    /**
     * Loads a class summary from disk.
     */
    Optional<ClassSummary> loadClassSummary(Path repositoryPath, String qualifiedName);

    /**
     * Saves an API endpoint summary to disk.
     */
    void saveApiSummary(Path repositoryPath, ApiSummary summary);

    /**
     * Loads an API endpoint summary from disk.
     */
    Optional<ApiSummary> loadApiSummary(Path repositoryPath, String endpointKey);

    /**
     * Persists a history snapshot for the repository.
     */
    void saveHistorySnapshot(Path repositoryPath, HistorySnapshot snapshot);

    /**
     * Lists recent history snapshots, newest first.
     */
    List<HistorySnapshot> listHistorySnapshots(Path repositoryPath, int limit);

    /**
     * Stores a cache entry under {@code .ai-memory/cache/}.
     */
    void putCacheEntry(Path repositoryPath, String key, String value);

    /**
     * Loads a cache entry.
     */
    Optional<String> getCacheEntry(Path repositoryPath, String key);

    /**
     * Clears all cache entries for a repository.
     */
    void clearCache(Path repositoryPath);

    /**
     * Returns a summary of persisted memory for a repository.
     */
    Optional<MemoryOverview> loadOverview(Path repositoryPath);

    /**
     * Removes all memory data for a repository.
     */
    void clean(Path repositoryPath);

    /**
     * Returns the path to the .ai-memory directory.
     */
    Path memoryPath(Path repositoryPath);
}
