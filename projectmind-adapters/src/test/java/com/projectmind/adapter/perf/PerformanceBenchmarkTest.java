package com.projectmind.adapter.perf;

import com.projectmind.adapter.scanner.FileSystemRepositoryScanner;
import com.projectmind.core.domain.RepositoryFile;
import com.projectmind.core.domain.RepositoryIndex;
import com.projectmind.core.domain.ScanStatistics;
import com.projectmind.core.port.ConfigurationPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Tag;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("benchmark")
class PerformanceBenchmarkTest {

    @TempDir
    Path tempDir;

    @Test
    void scansOneThousandFilesWithinReasonableTime() throws Exception {
        createJavaFiles(tempDir, 1000);

        FileSystemRepositoryScanner scanner = new FileSystemRepositoryScanner(benchmarkConfig());
        long start = System.nanoTime();
        var index = scanner.scan(tempDir, (phase, current, total, msg) -> {}, Set.of());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertThat(index.totalFiles()).isEqualTo(1000);
        assertThat(elapsedMs).isLessThan(30_000);
    }

    @Test
    void incrementalScanReusesHashesWhenMetadataUnchanged() throws Exception {
        createJavaFiles(tempDir, 50);
        FileSystemRepositoryScanner scanner = new FileSystemRepositoryScanner(benchmarkConfig());

        RepositoryIndex first = scanner.scan(tempDir, (phase, current, total, msg) -> {}, Set.of());
        Map<String, RepositoryFile> stored = Map.of(
                first.files().get(0).relativePath().toString(), first.files().get(0));

        long start = System.nanoTime();
        List<RepositoryFile> secondPass = scanner.scanStream(tempDir, stored).toList();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertThat(secondPass.get(0).contentHash()).isEqualTo(first.files().get(0).contentHash());
        assertThat(elapsedMs).isLessThan(5_000);
    }

    @Test
    void repositoryIndexMemoryEstimateForTenThousandFiles() {
        List<RepositoryFile> files = java.util.stream.IntStream.range(0, 10_000)
                .mapToObj(i -> new RepositoryFile(
                        Path.of("src/File" + i + ".java"),
                        Path.of("/repo/src/File" + i + ".java"),
                        com.projectmind.core.domain.FileType.JAVA,
                        "hash-" + i,
                        1024,
                        Instant.now()))
                .toList();

        RepositoryIndex index = new RepositoryIndex(
                Path.of("/repo"),
                Instant.now(),
                files.size(),
                files,
                ScanStatistics.fromFiles(files),
                0);

        Runtime runtime = Runtime.getRuntime();
        System.gc();
        long before = runtime.totalMemory() - runtime.freeMemory();
        long retained = index.totalFiles();
        long after = runtime.totalMemory() - runtime.freeMemory();

        assertThat(retained).isEqualTo(10_000);
        assertThat(after - before).isLessThan(50 * 1024 * 1024);
    }

    private static void createJavaFiles(Path root, int count) throws Exception {
        Path src = root.resolve("src/main/java/com/example");
        Files.createDirectories(src);
        for (int i = 0; i < count; i++) {
            Files.writeString(src.resolve("File" + i + ".java"),
                    "package com.example;\npublic class File" + i + " {}\n");
        }
    }

    private static ConfigurationPort benchmarkConfig() {
        int processors = Math.max(2, Runtime.getRuntime().availableProcessors());
        return new ConfigurationPort() {
            @Override public String getOllamaBaseUrl() { return "http://localhost:11434"; }
            @Override public String getOllamaModel() { return "test"; }
            @Override public String getChromaUrl() { return "http://localhost:8000"; }
            @Override public String getGlobalDbPath() { return ":memory:"; }
            @Override public int getScanBatchSize() { return 100; }
            @Override public List<String> getSkipDirectories() { return List.of(".git", "target"); }
            @Override public String getDocsOutputDir() { return "documentation"; }
            @Override public int getScanHashConcurrency() { return processors; }
            @Override public int getParseConcurrency() { return processors; }
            @Override public Optional<String> getProperty(String key) { return Optional.empty(); }
        };
    }
}
