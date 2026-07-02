package com.projectmind.core.path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryPathResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void expandsHomeDirectory() {
        String home = System.getProperty("user.home");
        Path resolved = RepositoryPathResolver.resolve("~/demo-repo");
        assertThat(resolved).isEqualTo(Path.of(home, "demo-repo").normalize());
    }

    @Test
    void normalizesRelativePathsToAbsolute() {
        Path resolved = RepositoryPathResolver.resolve(tempDir.resolve("nested").toString());
        assertThat(resolved).isEqualTo(tempDir.resolve("nested").toAbsolutePath().normalize());
    }

    @Test
    void storageKeyMatchesAbsoluteNormalizedPath() {
        Path input = tempDir.resolve("./project");
        assertThat(RepositoryPathResolver.toStorageKey(input))
                .isEqualTo(tempDir.resolve("project").toAbsolutePath().normalize().toString());
    }
}
