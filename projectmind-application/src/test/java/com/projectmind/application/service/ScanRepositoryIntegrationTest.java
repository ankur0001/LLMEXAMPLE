package com.projectmind.application.service;

import com.projectmind.adapter.dependency.GraphDependencyAnalyzer;
import com.projectmind.adapter.knowledge.DefaultKnowledgeGraphBuilder;
import com.projectmind.adapter.memory.JsonMemoryManager;
import com.projectmind.adapter.parser.treesitter.TreeSitterLanguageParser;
import com.projectmind.adapter.scanner.FileSystemRepositoryScanner;
import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.ScanStatus;
import com.projectmind.core.domain.SearchResult;
import com.projectmind.core.domain.VectorDocument;
import com.projectmind.core.port.PluginRegistryPort;
import com.projectmind.core.port.ProjectMindPlugin;
import com.projectmind.core.port.VectorIndexPort;
import com.projectmind.plugin.spring.SpringBootPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ScanRepositoryIntegrationTest {

    @TempDir
    Path tempDir;

    private ScanRepositoryUseCase scanUseCase;
    private JsonMemoryManager memoryManager;
    private final TestConfig testConfig = new TestConfig();

    @BeforeEach
    void setUp() {
        memoryManager = new JsonMemoryManager();
        var knowledgeGraphPort = new DefaultKnowledgeGraphBuilder(new GraphDependencyAnalyzer());
        var pluginEnhancementService = new PluginEnhancementService(new TestPluginRegistry(new SpringBootPlugin()));
        var graphBuilder = new RepositoryGraphBuilder(
                new TreeSitterLanguageParser(),
                knowledgeGraphPort,
                memoryManager,
                pluginEnhancementService,
                testConfig);
        var vectorIndexer = new RepositoryVectorIndexer(new NoOpVectorIndex(), memoryManager, testConfig);
        scanUseCase = new ScanRepositoryUseCase(
                new FileSystemRepositoryScanner(testConfig),
                memoryManager,
                graphBuilder,
                vectorIndexer,
                testConfig);
    }

    @Test
    void fullScanPersistsIndexStatisticsAndClearsProgress() throws Exception {
        createSampleRepository(tempDir);

        var metadata = scanUseCase.execute(tempDir, (phase, current, total, msg) -> {});

        assertThat(metadata.status()).isEqualTo(ScanStatus.INDEXED);
        assertThat(metadata.totalFiles()).isEqualTo(8);

        var index = memoryManager.loadIndex(tempDir).orElseThrow();
        assertThat(index.statistics().countFor(FileType.JAVA)).isEqualTo(2);
        assertThat(index.statistics().countFor(FileType.KUBERNETES)).isEqualTo(1);
        assertThat(index.scanDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(Files.exists(tempDir.resolve(".ai-memory/repository_index.json"))).isTrue();
        assertThat(Files.exists(tempDir.resolve(".ai-memory/dependency_graph.json"))).isTrue();
        assertThat(Files.exists(tempDir.resolve(".ai-memory/diagrams/dependency_graph.mmd"))).isTrue();
        assertThat(memoryManager.loadGraph(tempDir)).isPresent();
        assertThat(memoryManager.loadScanProgress(tempDir)).isEmpty();
        assertThat(memoryManager.loadScanCheckpoint(tempDir)).isEmpty();
    }

    private void createSampleRepository(Path root) throws Exception {
        Files.createDirectories(root.resolve("src/main/java/com/example"));
        Files.writeString(root.resolve("src/main/java/com/example/App.java"),
                "package com.example;\npublic class App {}");
        Files.writeString(root.resolve("src/main/java/com/example/Service.java"),
                "package com.example;\npublic class Service {}");
        Files.writeString(root.resolve("pom.xml"), "<project></project>");
        Files.createDirectories(root.resolve("src/main/resources"));
        Files.writeString(root.resolve("src/main/resources/application.yaml"), "server:\n  port: 8080\n");
        Files.writeString(root.resolve("Dockerfile"), "FROM eclipse-temurin:21\n");
        Files.createDirectories(root.resolve("k8s"));
        Files.writeString(root.resolve("k8s/deployment.yaml"), "apiVersion: apps/v1\nkind: Deployment\n");
        Files.writeString(root.resolve("README.md"), "# Sample\n");
        Files.createDirectories(root.resolve("scripts"));
        Files.writeString(root.resolve("scripts/run.sh"), "#!/bin/bash\necho hi\n");
        Files.createDirectories(root.resolve("target/classes"));
        Files.writeString(root.resolve("target/classes/Ignored.class"), "ignored");
    }

    private static class TestConfig implements com.projectmind.core.port.ConfigurationPort {
        @Override public String getOllamaBaseUrl() { return "http://localhost:11434"; }
        @Override public String getOllamaModel() { return "test"; }
        @Override public String getChromaUrl() { return "http://localhost:8000"; }
        @Override public String getGlobalDbPath() { return "/tmp/test.db"; }
        @Override public int getScanBatchSize() { return 3; }
        @Override public List<String> getSkipDirectories() {
            return List.of(".git", "target", "build", "node_modules", "dist", "out", ".ai-memory");
        }
        @Override public String getDocsOutputDir() { return "documentation"; }
        @Override public java.util.Optional<String> getProperty(String key) { return java.util.Optional.empty(); }
    }

    private static final class NoOpVectorIndex implements VectorIndexPort {
        @Override public void index(Path repositoryPath, List<VectorDocument> documents) { }
        @Override public List<SearchResult> search(Path repositoryPath, String query, int topK, FileType fileTypeFilter) {
            return List.of();
        }
        @Override public void remove(Path repositoryPath, List<String> relativePaths) { }
        @Override public long count(Path repositoryPath) { return 0; }
    }

    private static final class TestPluginRegistry implements PluginRegistryPort {
        private final ProjectMindPlugin plugin;

        private TestPluginRegistry(ProjectMindPlugin plugin) {
            this.plugin = plugin;
        }

        @Override public void initialize(com.projectmind.core.port.PluginContext context) { }
        @Override public void shutdown() { }
        @Override public List<ProjectMindPlugin> getPlugins() { return List.of(plugin); }
        @Override public List<ProjectMindPlugin> getEnabledPlugins() { return List.of(plugin); }
        @Override public Optional<ProjectMindPlugin> findByName(String name) {
            return plugin.getName().equals(name) ? Optional.of(plugin) : Optional.empty();
        }
    }
}
