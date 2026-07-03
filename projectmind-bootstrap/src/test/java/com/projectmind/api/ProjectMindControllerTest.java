package com.projectmind.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectmind.api.dto.AskRequest;
import com.projectmind.api.dto.ExportRequest;
import com.projectmind.api.dto.RepositoryPathRequest;
import com.projectmind.application.service.AskQuestionUseCase;
import com.projectmind.application.service.ExportRepositoryUseCase;
import com.projectmind.application.service.GenerateDocumentationUseCase;
import com.projectmind.application.service.GraphQueryUseCase;
import com.projectmind.application.service.MemoryOverviewUseCase;
import com.projectmind.application.service.RepositoryEnrichmentUseCase;
import com.projectmind.application.service.RepositoryStatusUseCase;
import com.projectmind.application.service.ResumeScanRepositoryUseCase;
import com.projectmind.application.service.ScanRepositoryUseCase;
import com.projectmind.application.service.UpdateRepositoryUseCase;
import com.projectmind.core.domain.AskResponse;
import com.projectmind.core.domain.FileChangeSet;
import com.projectmind.core.domain.HistorySnapshot;
import com.projectmind.core.domain.KnowledgeGraph;
import com.projectmind.core.domain.MemoryOverview;
import com.projectmind.core.domain.OllamaModelInfo;
import com.projectmind.core.domain.ProjectMetadata;
import com.projectmind.core.domain.ScanStatus;
import com.projectmind.core.port.MemoryManagerPort;
import com.projectmind.core.port.OllamaClientPort;
import com.projectmind.core.port.PluginRegistryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectMindController.class)
@Import(GlobalExceptionHandler.class)
class ProjectMindControllerTest {

    private static final String REPO = "/tmp/test-repo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ScanRepositoryUseCase scanUseCase;

    @MockBean
    private ResumeScanRepositoryUseCase resumeUseCase;

    @MockBean
    private UpdateRepositoryUseCase updateUseCase;

    @MockBean
    private GenerateDocumentationUseCase docsUseCase;

    @MockBean
    private AskQuestionUseCase askUseCase;

    @MockBean
    private ExportRepositoryUseCase exportUseCase;

    @MockBean
    private RepositoryStatusUseCase statusUseCase;

    @MockBean
    private GraphQueryUseCase graphQueryUseCase;

    @MockBean
    private MemoryOverviewUseCase memoryOverviewUseCase;

    @MockBean
    private RepositoryEnrichmentUseCase enrichmentUseCase;

    @MockBean
    private MemoryManagerPort memoryManager;

    @MockBean
    private PluginRegistryPort pluginRegistry;

    @MockBean
    private OllamaClientPort ollamaClient;

    @Test
    void pluginsReturnsRegistry() throws Exception {
        when(pluginRegistry.getPlugins()).thenReturn(List.of());
        when(pluginRegistry.getEnabledPlugins()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/plugins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plugins").isArray());
    }

    @Test
    void statusReturnsMetadataWhenFound() throws Exception {
        ProjectMetadata metadata = sampleMetadata();
        when(statusUseCase.execute(Path.of(REPO))).thenReturn(Optional.of(metadata));

        mockMvc.perform(get("/api/v1/status").param("path", REPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("test-repo"))
                .andExpect(jsonPath("$.status").value("INDEXED"));
    }

    @Test
    void statusReturnsNotFoundWhenMissing() throws Exception {
        when(statusUseCase.execute(Path.of(REPO))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/status").param("path", REPO))
                .andExpect(status().isNotFound());
    }

    @Test
    void scanReturnsMetadata() throws Exception {
        ProjectMetadata metadata = sampleMetadata();
        when(scanUseCase.execute(eq(Path.of(REPO)), any())).thenReturn(metadata);

        mockMvc.perform(post("/api/v1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RepositoryPathRequest(REPO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositoryPath").value(REPO));
    }

    @Test
    void scanRejectsBlankPath() throws Exception {
        mockMvc.perform(post("/api/v1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void resumeReturnsMetadata() throws Exception {
        ProjectMetadata metadata = sampleMetadata();
        when(resumeUseCase.execute(eq(Path.of(REPO)), any())).thenReturn(metadata);

        mockMvc.perform(post("/api/v1/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RepositoryPathRequest(REPO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INDEXED"));
    }

    @Test
    void updateReturnsChangeSet() throws Exception {
        FileChangeSet changes = new FileChangeSet(List.of(), List.of(), List.of());
        when(updateUseCase.execute(eq(Path.of(REPO)), any())).thenReturn(changes);

        mockMvc.perform(post("/api/v1/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RepositoryPathRequest(REPO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").isArray());
    }

    @Test
    void docsReturnsOutputPath() throws Exception {
        when(docsUseCase.execute(Path.of(REPO))).thenReturn(Path.of(REPO, "documentation", "index.html"));

        mockMvc.perform(post("/api/v1/docs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RepositoryPathRequest(REPO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexPath").value(REPO + "/documentation/index.html"));
    }

    @Test
    void exportReturnsExportedPath() throws Exception {
        when(exportUseCase.execute(Path.of(REPO), Path.of(".")))
                .thenReturn(Path.of(".", "projectmind-export"));

        mockMvc.perform(post("/api/v1/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExportRequest(REPO, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportPath").value("./projectmind-export"));
    }

    @Test
    void ollamaModelsReturnsInstalledModels() throws Exception {
        when(ollamaClient.listModels()).thenReturn(List.of(
                new OllamaModelInfo("phi3:latest", List.of("completion")),
                new OllamaModelInfo("nomic-embed-text:latest", List.of("embedding"))));

        mockMvc.perform(get("/api/v1/ollama/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models.length()").value(2))
                .andExpect(jsonPath("$.models[0].name").value("phi3:latest"));
    }

    @Test
    void askReturnsAnswer() throws Exception {
        AskResponse response = new AskResponse("How?", "Because.", List.of("Main.java"), "qwen");
        when(askUseCase.execute(Path.of(REPO), "How?", null)).thenReturn(response);

        mockMvc.perform(post("/api/v1/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AskRequest(REPO, "How?", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Because."));
    }

    @Test
    void askUsesSelectedModel() throws Exception {
        AskResponse response = new AskResponse("How?", "Because.", List.of("Main.java"), "phi3:latest");
        when(askUseCase.execute(Path.of(REPO), "How?", "phi3:latest")).thenReturn(response);

        mockMvc.perform(post("/api/v1/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AskRequest(REPO, "How?", "phi3:latest"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("phi3:latest"));
    }

    @Test
    void askRejectsBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"" + REPO + "\",\"question\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void memoryReturnsOverview() throws Exception {
        MemoryOverview overview = new MemoryOverview(
                sampleMetadata(), 10, 5, 3, 2, 1, 0, 1, 0, List.of());
        when(memoryOverviewUseCase.execute(Path.of(REPO))).thenReturn(Optional.of(overview));

        mockMvc.perform(get("/api/v1/memory").param("path", REPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graphNodeCount").value(10));
    }

    @Test
    void memoryReturnsEmptyOverviewWhenNotScanned() throws Exception {
        MemoryOverview overview = new MemoryOverview(
                new ProjectMetadata(
                        "test-repo",
                        REPO,
                        ScanStatus.NOT_SCANNED,
                        null,
                        null,
                        null,
                        0,
                        0,
                        null,
                        Map.of()),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                List.of());
        when(memoryOverviewUseCase.execute(Path.of(REPO))).thenReturn(Optional.of(overview));

        mockMvc.perform(get("/api/v1/memory").param("path", REPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.status").value("NOT_SCANNED"))
                .andExpect(jsonPath("$.graphNodeCount").value(0));
    }

    @Test
    void historyReturnsSnapshots() throws Exception {
        HistorySnapshot snapshot = new HistorySnapshot(
                Instant.parse("2026-07-02T12:00:00Z"), "update", 2, "Updated files");
        when(memoryManager.loadMetadata(Path.of(REPO))).thenReturn(Optional.of(sampleMetadata()));
        when(memoryManager.listHistorySnapshots(Path.of(REPO), 5)).thenReturn(List.of(snapshot));

        mockMvc.perform(get("/api/v1/history").param("path", REPO).param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].operation").value("update"));
    }

    @Test
    void historyReturnsNotFoundWhenNoMetadata() throws Exception {
        when(memoryManager.loadMetadata(Path.of(REPO))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/history").param("path", REPO))
                .andExpect(status().isNotFound());
    }

    @Test
    void graphReturnsKnowledgeGraph() throws Exception {
        KnowledgeGraph graph = new KnowledgeGraph(List.of(), List.of());
        when(graphQueryUseCase.execute(Path.of(REPO), null, 1, null, null))
                .thenReturn(Optional.of(graph));

        mockMvc.perform(get("/api/v1/graph").param("path", REPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isArray());
    }

    @Test
    void graphMermaidReturnsPlainText() throws Exception {
        when(graphQueryUseCase.mermaid(Path.of(REPO), null, 1, null, null))
                .thenReturn(Optional.of("graph TD\n  A-->B"));

        mockMvc.perform(get("/api/v1/graph/mermaid").param("path", REPO))
                .andExpect(status().isOk())
                .andExpect(content().string("graph TD\n  A-->B"));
    }

    @Test
    void clearCacheReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/cache").param("path", REPO))
                .andExpect(status().isNoContent());

        verify(memoryManager).clearCache(Path.of(REPO));
    }

    @Test
    void cleanReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/memory").param("path", REPO))
                .andExpect(status().isNoContent());

        verify(memoryManager).clean(Path.of(REPO));
    }

    @Test
    void illegalStateReturnsConflict() throws Exception {
        when(scanUseCase.execute(eq(Path.of(REPO)), any()))
                .thenThrow(new IllegalStateException("Scan already in progress"));

        mockMvc.perform(post("/api/v1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RepositoryPathRequest(REPO))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Scan already in progress"));
    }

    private static ProjectMetadata sampleMetadata() {
        Instant now = Instant.parse("2026-07-02T12:00:00Z");
        return new ProjectMetadata(
                "test-repo",
                REPO,
                ScanStatus.INDEXED,
                now,
                now,
                now,
                10,
                10,
                "qwen2.5-coder:14b-instruct",
                Map.of());
    }
}
