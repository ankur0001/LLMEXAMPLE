package com.projectmind.adapter.scanner;

import com.projectmind.core.domain.FileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileTypeDetectorTest {

    private FileTypeDetector detector;

    @TempDir
    Path repoRoot;

    @BeforeEach
    void setUp() {
        detector = new FileTypeDetector();
    }

    @Test
    void detectsJavaAndMaven() {
        assertThat(detector.detect(repoRoot.resolve("src/App.java"), repoRoot)).isEqualTo(FileType.JAVA);
        assertThat(detector.detect(repoRoot.resolve("pom.xml"), repoRoot)).isEqualTo(FileType.MAVEN);
    }

    @Test
    void detectsDockerFiles() {
        assertThat(detector.detect(repoRoot.resolve("Dockerfile"), repoRoot)).isEqualTo(FileType.DOCKER);
        assertThat(detector.detect(repoRoot.resolve("docker-compose.yml"), repoRoot)).isEqualTo(FileType.DOCKER);
    }

    @Test
    void detectsKubernetesManifestsByDirectory() {
        Path manifest = repoRoot.resolve("k8s/deployment.yaml");
        assertThat(detector.detect(manifest, repoRoot)).isEqualTo(FileType.KUBERNETES);
    }

    @Test
    void detectsKubernetesManifestsByFilename() {
        Path manifest = repoRoot.resolve("app-service.yaml");
        assertThat(detector.detect(manifest, repoRoot)).isEqualTo(FileType.KUBERNETES);
    }

    @Test
    void detectsGradleKotlinShellAndSql() {
        assertThat(detector.detect(repoRoot.resolve("build.gradle.kts"), repoRoot)).isEqualTo(FileType.GRADLE);
        assertThat(detector.detect(repoRoot.resolve("Module.kt"), repoRoot)).isEqualTo(FileType.KOTLIN);
        assertThat(detector.detect(repoRoot.resolve("scripts/run.sh"), repoRoot)).isEqualTo(FileType.SHELL);
        assertThat(detector.detect(repoRoot.resolve("schema.sql"), repoRoot)).isEqualTo(FileType.SQL);
    }

    @Test
    void plainYamlOutsideK8sDirsIsYaml() {
        Path config = repoRoot.resolve("config/application.yaml");
        assertThat(detector.detect(config, repoRoot)).isEqualTo(FileType.YAML);
    }
}
