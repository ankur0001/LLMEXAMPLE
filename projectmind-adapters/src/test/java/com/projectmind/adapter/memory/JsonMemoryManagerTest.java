package com.projectmind.adapter.memory;

import com.projectmind.core.domain.ApiSummary;
import com.projectmind.core.domain.ClassSummary;
import com.projectmind.core.domain.FileSummary;
import com.projectmind.core.domain.GraphEdge;
import com.projectmind.core.domain.GraphEdgeType;
import com.projectmind.core.domain.GraphNode;
import com.projectmind.core.domain.GraphNodeType;
import com.projectmind.core.domain.HistorySnapshot;
import com.projectmind.core.domain.KnowledgeGraph;
import com.projectmind.core.domain.PackageSummary;
import com.projectmind.core.domain.ProjectMetadata;
import com.projectmind.core.domain.ScanStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonMemoryManagerTest {

    @TempDir
    Path tempDir;

    private JsonMemoryManager memoryManager;

    @BeforeEach
    void setUp() {
        memoryManager = new JsonMemoryManager();
    }

    @Test
    void initializeMemoryCreatesExpectedLayout() {
        Path memoryDir = memoryManager.initializeMemory(tempDir);

        assertThat(Files.isDirectory(memoryDir.resolve("summaries"))).isTrue();
        assertThat(Files.isDirectory(memoryDir.resolve("package_summaries"))).isTrue();
        assertThat(Files.isDirectory(memoryDir.resolve("class_summaries"))).isTrue();
        assertThat(Files.isDirectory(memoryDir.resolve("api_summaries"))).isTrue();
        assertThat(Files.isDirectory(memoryDir.resolve("history"))).isTrue();
        assertThat(Files.isDirectory(memoryDir.resolve("cache"))).isTrue();
    }

    @Test
    void storesAllSummaryTypesAndHistory() {
        memoryManager.initializeMemory(tempDir);

        ProjectMetadata metadata = new ProjectMetadata(
                "demo",
                tempDir.toString(),
                ScanStatus.INDEXED,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                3,
                3,
                "test-model",
                Map.of());
        memoryManager.saveMetadata(tempDir, metadata);

        memoryManager.saveSummary(tempDir, new FileSummary(
                "src/App.java", "Main application entry point", "Bootstrap", List.of("Spring")));
        memoryManager.savePackageSummary(tempDir, new PackageSummary(
                "com.example", "Core package", List.of("App")));
        memoryManager.saveClassSummary(tempDir, new ClassSummary(
                "com.example.App", "Application class", "bootstrap", List.of("starts app")));
        memoryManager.saveApiSummary(tempDir, new ApiSummary(
                "/api/users", "GET", "List users", "UserController"));

        memoryManager.saveGraph(tempDir, new KnowledgeGraph(
                List.of(new GraphNode("type:com.example.App", "App", GraphNodeType.CLASS, "src/App.java", "com.example")),
                List.of(new GraphEdge("type:com.example.App", "import:java.lang.Object", GraphEdgeType.EXTENDS, "Object"))));

        memoryManager.saveHistorySnapshot(tempDir, new HistorySnapshot(
                Instant.parse("2026-07-02T10:00:00Z"), "scan", 3, "Initial scan"));

        assertThat(memoryManager.loadSummary(tempDir, "src/App.java")).isPresent();
        assertThat(memoryManager.loadPackageSummary(tempDir, "com.example")).isPresent();
        assertThat(memoryManager.loadClassSummary(tempDir, "com.example.App")).isPresent();
        assertThat(memoryManager.loadApiSummary(tempDir, "GET_/api/users")).isPresent();
        assertThat(memoryManager.listHistorySnapshots(tempDir, 5)).hasSize(1);

        var overview = memoryManager.loadOverview(tempDir).orElseThrow();
        assertThat(overview.fileSummaryCount()).isEqualTo(1);
        assertThat(overview.packageSummaryCount()).isEqualTo(1);
        assertThat(overview.classSummaryCount()).isEqualTo(1);
        assertThat(overview.apiSummaryCount()).isEqualTo(1);
        assertThat(overview.graphNodeCount()).isEqualTo(1);
        assertThat(overview.historyEntryCount()).isEqualTo(1);
    }

    @Test
    void managesCacheEntries() {
        memoryManager.initializeMemory(tempDir);
        memoryManager.putCacheEntry(tempDir, "embedding:App.java", "cached-vector");
        assertThat(memoryManager.getCacheEntry(tempDir, "embedding:App.java")).contains("cached-vector");

        memoryManager.clearCache(tempDir);
        assertThat(memoryManager.getCacheEntry(tempDir, "embedding:App.java")).isEmpty();
    }

    @Test
    void cleanRemovesMemoryDirectoryAndSqliteProject() {
        memoryManager.initializeMemory(tempDir);
        memoryManager.saveMetadata(tempDir, new ProjectMetadata(
                "demo", tempDir.toString(), ScanStatus.INDEXED,
                Instant.now(), Instant.now(), Instant.now(),
                0, 0, "test-model", Map.of()));

        memoryManager.clean(tempDir);

        assertThat(Files.exists(memoryManager.memoryPath(tempDir))).isFalse();
        assertThat(memoryManager.loadOverview(tempDir)).isEmpty();
    }
}
