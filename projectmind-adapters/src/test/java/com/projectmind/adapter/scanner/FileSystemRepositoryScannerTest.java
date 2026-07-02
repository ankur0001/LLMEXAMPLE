package com.projectmind.adapter.scanner;

import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.RepositoryFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemRepositoryScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void scanDetectsJavaFiles() throws Exception {
        Files.createDirectories(tempDir.resolve("src/main/java/com/example"));
        Files.writeString(tempDir.resolve("src/main/java/com/example/App.java"),
                "package com.example;\npublic class App {}");
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");

        var scanner = new FileSystemRepositoryScanner(new TestConfig(List.of(".git", "target")));

        var index = scanner.scan(tempDir, (phase, current, total, msg) -> {});

        assertThat(index.totalFiles()).isEqualTo(2);
        assertThat(index.statistics().countFor(FileType.JAVA)).isEqualTo(1);
        assertThat(index.statistics().countFor(FileType.MAVEN)).isEqualTo(1);
        assertThat(index.scanDurationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void scanSkipsIgnoredDirectories() throws Exception {
        Files.createDirectories(tempDir.resolve("target/classes"));
        Files.writeString(tempDir.resolve("target/classes/App.class"), "binary");
        Files.writeString(tempDir.resolve("App.java"), "class App {}");

        var scanner = new FileSystemRepositoryScanner(new TestConfig(List.of("target")));

        var index = scanner.scan(tempDir, (phase, current, total, msg) -> {});

        assertThat(index.totalFiles()).isEqualTo(1);
        assertThat(index.files().get(0).fileType()).isEqualTo(FileType.JAVA);
    }

    @Test
    void scanSkipsEntireSubtreeForBuildDirectories() throws Exception {
        Files.createDirectories(tempDir.resolve("build/generated/deep"));
        Files.writeString(tempDir.resolve("build/generated/deep/Generated.java"), "class G {}");
        Files.writeString(tempDir.resolve("Main.java"), "class Main {}");

        var scanner = new FileSystemRepositoryScanner(new TestConfig(List.of("build")));

        var index = scanner.scan(tempDir, (phase, current, total, msg) -> {});

        assertThat(index.totalFiles()).isEqualTo(1);
    }

    @Test
    void computeHashIsDeterministic() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello world");

        var scanner = new FileSystemRepositoryScanner(new TestConfig(List.of()));
        var file1 = scanner.toRepositoryFile(file, tempDir);
        var file2 = scanner.toRepositoryFile(file, tempDir);

        assertThat(file1.contentHash()).isEqualTo(file2.contentHash());
        assertThat(file1.contentHash()).isNotEqualTo("unknown");
    }

    @Test
    void scanReportsProgressDuringProcessing() throws Exception {
        for (int i = 0; i < 5; i++) {
            Files.writeString(tempDir.resolve("File" + i + ".java"), "class F" + i + " {}");
        }

        var scanner = new FileSystemRepositoryScanner(new TestConfig(List.of(), 2));
        AtomicInteger progressEvents = new AtomicInteger();

        scanner.scan(tempDir, (phase, current, total, msg) -> {
            if ("scan".equals(phase)) {
                progressEvents.incrementAndGet();
            }
        });

        assertThat(progressEvents.get()).isGreaterThan(1);
    }

    @Test
    void scanResumeSkipsAlreadyIndexedPaths() throws Exception {
        Files.writeString(tempDir.resolve("A.java"), "class A {}");
        Files.writeString(tempDir.resolve("B.java"), "class B {}");

        var scanner = new FileSystemRepositoryScanner(new TestConfig(List.of()));
        var first = scanner.scan(tempDir, (p, c, t, m) -> {});

        var resumed = scanner.scan(tempDir, (p, c, t, m) -> {}, Set.of("A.java"));

        assertThat(resumed.files()).hasSize(1);
        assertThat(resumed.files().get(0).relativePath().toString()).isEqualTo("B.java");
        assertThat(first.files()).hasSize(2);
    }

    @Test
    void scanInvokesBatchSnapshot() throws Exception {
        Files.writeString(tempDir.resolve("A.java"), "class A {}");
        Files.writeString(tempDir.resolve("B.java"), "class B {}");

        var scanner = new FileSystemRepositoryScanner(new TestConfig(List.of(), 1));
        List<List<RepositoryFile>> snapshots = new ArrayList<>();

        scanner.scan(tempDir, (p, c, t, m) -> {}, Set.of(), snapshots::add);

        assertThat(snapshots).isNotEmpty();
        assertThat(snapshots.get(snapshots.size() - 1)).hasSize(2);
    }

    @Test
    void rejectsNonDirectoryPath() {
        var scanner = new FileSystemRepositoryScanner(new TestConfig(List.of()));
        assertThatThrownBy(() -> scanner.scan(tempDir.resolve("missing"), (p, c, t, m) -> {}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scanHandlesLargeRepository() throws Exception {
        Files.createDirectories(tempDir.resolve("src"));
        for (int i = 0; i < 100; i++) {
            Files.writeString(tempDir.resolve("src/File" + i + ".java"), "class F" + i + " {}");
        }

        var scanner = new FileSystemRepositoryScanner(new TestConfig(List.of(), 50));
        var index = scanner.scan(tempDir, (p, c, t, m) -> {});

        assertThat(index.totalFiles()).isEqualTo(100);
        assertThat(index.statistics().countFor(FileType.JAVA)).isEqualTo(100);
        assertThat(index.statistics().totalBytes()).isPositive();
    }

    private static class TestConfig implements com.projectmind.core.port.ConfigurationPort {
        private final List<String> skipDirs;
        private final int batchSize;

        TestConfig(List<String> skipDirs) {
            this(skipDirs, 100);
        }

        TestConfig(List<String> skipDirs, int batchSize) {
            this.skipDirs = skipDirs;
            this.batchSize = batchSize;
        }

        @Override public String getOllamaBaseUrl() { return "http://localhost:11434"; }
        @Override public String getOllamaModel() { return "test"; }
        @Override public String getChromaUrl() { return "http://localhost:8000"; }
        @Override public String getGlobalDbPath() { return "/tmp/test.db"; }
        @Override public int getScanBatchSize() { return batchSize; }
        @Override public List<String> getSkipDirectories() { return skipDirs; }
        @Override public String getDocsOutputDir() { return "documentation"; }
        @Override public java.util.Optional<String> getProperty(String key) { return java.util.Optional.empty(); }
    }
}
