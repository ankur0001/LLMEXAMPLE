package com.projectmind.application.service;

import com.projectmind.adapter.memory.JsonMemoryManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExportRepositoryUseCaseTest {

    @TempDir
    Path tempDir;

    @Test
    void exportsMemoryDirectory() throws Exception {
        Path repo = tempDir.resolve("repo");
        Path exportDir = tempDir.resolve("export");
        Files.createDirectories(repo.resolve(".ai-memory/summaries"));
        Files.writeString(repo.resolve(".ai-memory/project.json"), "{\"name\":\"demo\"}");

        JsonMemoryManager memory = new JsonMemoryManager();
        ExportRepositoryUseCase useCase = new ExportRepositoryUseCase(memory);

        Path exported = useCase.execute(repo, exportDir);

        assertThat(exported).isEqualTo(exportDir.resolve(".ai-memory").toAbsolutePath().normalize());
        assertThat(Files.exists(exported.resolve("project.json"))).isTrue();
    }
}
