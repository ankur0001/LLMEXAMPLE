package com.projectmind.adapter.html;

import com.projectmind.core.domain.DocSection;
import com.projectmind.core.domain.DocSectionType;
import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.KnowledgeGraph;
import com.projectmind.core.domain.ProjectMetadata;
import com.projectmind.core.domain.RepositoryFile;
import com.projectmind.core.domain.RepositoryIndex;
import com.projectmind.core.domain.ScanStatus;
import com.projectmind.core.port.ConfigurationPort;
import com.projectmind.core.port.MemoryManagerPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResponsiveHtmlGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesResponsiveHtmlSite() throws Exception {
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo.resolve(".ai-memory"));

        MemoryManagerPort memory = mock(MemoryManagerPort.class);
        ConfigurationPort config = mock(ConfigurationPort.class);
        when(memory.memoryPath(repo)).thenReturn(repo.resolve(".ai-memory"));
        when(config.getDocsOutputDir()).thenReturn("documentation");

        RepositoryIndex index = new RepositoryIndex(
                repo,
                Instant.now(),
                2,
                List.of(
                        file(repo, "src/main/java/com/example/App.java", FileType.JAVA),
                        file(repo, "pom.xml", FileType.MAVEN)));
        when(memory.loadIndex(repo)).thenReturn(Optional.of(index));
        when(memory.loadGraph(repo)).thenReturn(Optional.of(new KnowledgeGraph(List.of(), List.of())));

        ProjectMetadata metadata = new ProjectMetadata(
                "demo", repo.toString(), ScanStatus.INDEXED,
                Instant.now(), Instant.now(), Instant.now(),
                2, 2, "test", Map.of());

        List<DocSection> sections = List.of(
                new DocSection(
                        DocSectionType.REPOSITORY_OVERVIEW,
                        "Repository Overview",
                        "# Overview\n\n**Files:** 2\n",
                        null),
                new DocSection(
                        DocSectionType.DEPENDENCY_GRAPH,
                        "Dependency Graph",
                        "# Graph\n",
                        "graph TD\n  A-->B"));

        ResponsiveHtmlGenerator generator = new ResponsiveHtmlGenerator(memory, config);
        Path indexPath = generator.generate(repo, metadata, sections);

        assertThat(Files.exists(indexPath)).isTrue();
        String html = Files.readString(indexPath);
        assertThat(html).contains("data-theme");
        assertThat(html).contains("id=\"search\"");
        assertThat(html).contains("id=\"theme-toggle\"");
        assertThat(html).contains("id=\"expand-all\"");
        assertThat(html).contains("class=\"sidebar\"");
        assertThat(html).contains("class=\"dashboard\"");
        assertThat(html).contains("package-explorer");
        assertThat(html).contains("id=\"breadcrumb\"");
        assertThat(html).contains("class=\"mermaid\"");
        assertThat(html).contains("highlight.js");
        assertThat(html).contains("<strong>Files:</strong>");
        assertThat(html).contains("com.example");
    }

    private static RepositoryFile file(Path repo, String relative, FileType type) {
        return new RepositoryFile(
                Path.of(relative),
                repo.resolve(relative),
                type,
                "hash",
                100,
                Instant.now());
    }
}
