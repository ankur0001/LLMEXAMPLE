package com.projectmind.api;

import com.projectmind.core.path.RepositoryPathResolver;
import com.projectmind.application.service.AskQuestionUseCase;
import com.projectmind.application.service.ExportRepositoryUseCase;
import com.projectmind.application.service.GenerateDocumentationUseCase;
import com.projectmind.application.service.GraphQueryUseCase;
import com.projectmind.application.service.MemoryOverviewUseCase;
import com.projectmind.application.service.RepositoryStatusUseCase;
import com.projectmind.application.service.ResumeScanRepositoryUseCase;
import com.projectmind.application.service.ScanRepositoryUseCase;
import com.projectmind.application.service.UpdateRepositoryUseCase;
import com.projectmind.core.domain.AskResponse;
import com.projectmind.core.domain.FileChangeSet;
import com.projectmind.core.domain.GraphNodeType;
import com.projectmind.core.domain.HistorySnapshot;
import com.projectmind.core.domain.KnowledgeGraph;
import com.projectmind.core.domain.MemoryOverview;
import com.projectmind.core.domain.ProgressCallback;
import com.projectmind.core.domain.ProjectMetadata;
import com.projectmind.core.port.MemoryManagerPort;
import com.projectmind.core.port.OllamaClientPort;
import com.projectmind.core.port.PluginRegistryPort;
import com.projectmind.core.path.RepositoryPathResolver;
import com.projectmind.api.dto.AskRequest;
import com.projectmind.api.dto.DocsResponse;
import com.projectmind.api.dto.ExportRequest;
import com.projectmind.api.dto.ExportResponse;
import com.projectmind.api.dto.PluginInfo;
import com.projectmind.api.dto.OllamaModelDto;
import com.projectmind.api.dto.OllamaModelsResponse;
import com.projectmind.api.dto.PluginsResponse;
import com.projectmind.api.dto.RepositoryPathRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * REST API for ProjectMind operations.
 */
@RestController
@RequestMapping("/api/v1")
@Validated
@Tag(name = "ProjectMind", description = "Repository memory, graph, docs, and Q&A")
public class ProjectMindController {

    private final ScanRepositoryUseCase scanUseCase;
    private final ResumeScanRepositoryUseCase resumeUseCase;
    private final UpdateRepositoryUseCase updateUseCase;
    private final GenerateDocumentationUseCase docsUseCase;
    private final AskQuestionUseCase askUseCase;
    private final ExportRepositoryUseCase exportUseCase;
    private final RepositoryStatusUseCase statusUseCase;
    private final GraphQueryUseCase graphQueryUseCase;
    private final MemoryOverviewUseCase memoryOverviewUseCase;
    private final MemoryManagerPort memoryManager;
    private final PluginRegistryPort pluginRegistry;
    private final OllamaClientPort ollamaClient;

    public ProjectMindController(
            ScanRepositoryUseCase scanUseCase,
            ResumeScanRepositoryUseCase resumeUseCase,
            UpdateRepositoryUseCase updateUseCase,
            GenerateDocumentationUseCase docsUseCase,
            AskQuestionUseCase askUseCase,
            ExportRepositoryUseCase exportUseCase,
            RepositoryStatusUseCase statusUseCase,
            GraphQueryUseCase graphQueryUseCase,
            MemoryOverviewUseCase memoryOverviewUseCase,
            MemoryManagerPort memoryManager,
            PluginRegistryPort pluginRegistry,
            OllamaClientPort ollamaClient) {
        this.scanUseCase = scanUseCase;
        this.resumeUseCase = resumeUseCase;
        this.updateUseCase = updateUseCase;
        this.docsUseCase = docsUseCase;
        this.askUseCase = askUseCase;
        this.exportUseCase = exportUseCase;
        this.statusUseCase = statusUseCase;
        this.graphQueryUseCase = graphQueryUseCase;
        this.memoryOverviewUseCase = memoryOverviewUseCase;
        this.memoryManager = memoryManager;
        this.pluginRegistry = pluginRegistry;
        this.ollamaClient = ollamaClient;
    }

    @GetMapping("/plugins")
    @Operation(summary = "List discovered and enabled plugins")
    public ResponseEntity<PluginsResponse> plugins() {
        List<PluginInfo> plugins = pluginRegistry.getPlugins().stream()
                .map(plugin -> new PluginInfo(
                        plugin.getName(),
                        pluginRegistry.getEnabledPlugins().contains(plugin)))
                .toList();
        return ResponseEntity.ok(new PluginsResponse(plugins));
    }

    @GetMapping("/ollama/models")
    @Operation(summary = "List models installed in local Ollama")
    public ResponseEntity<OllamaModelsResponse> ollamaModels() {
        List<OllamaModelDto> models = ollamaClient.listModels().stream()
                .map(model -> new OllamaModelDto(model.name(), model.capabilities()))
                .toList();
        return ResponseEntity.ok(new OllamaModelsResponse(models));
    }

    @GetMapping("/status")
    @Operation(summary = "Get repository scan status")
    public ResponseEntity<ProjectMetadata> status(@RequestParam String path) {
        return statusUseCase.execute(repo(path))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/scan")
    @Operation(summary = "Scan a repository and build persistent memory")
    public ResponseEntity<ProjectMetadata> scan(@Valid @RequestBody RepositoryPathRequest request) {
        ProjectMetadata result = scanUseCase.execute(repo(request.path()), ProgressCallback.noop());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/resume")
    @Operation(summary = "Resume an interrupted repository scan")
    public ResponseEntity<ProjectMetadata> resume(@Valid @RequestBody RepositoryPathRequest request) {
        ProjectMetadata result = resumeUseCase.execute(repo(request.path()), ProgressCallback.noop());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/update")
    @Operation(summary = "Incrementally update repository memory")
    public ResponseEntity<FileChangeSet> update(@Valid @RequestBody RepositoryPathRequest request) {
        FileChangeSet changes = updateUseCase.execute(repo(request.path()), ProgressCallback.noop());
        return ResponseEntity.ok(changes);
    }

    @PostMapping("/docs")
    @Operation(summary = "Generate HTML documentation")
    public ResponseEntity<DocsResponse> docs(@Valid @RequestBody RepositoryPathRequest request) {
        Path outputPath = docsUseCase.execute(repo(request.path()));
        return ResponseEntity.ok(new DocsResponse(outputPath.toString()));
    }

    @GetMapping(value = "/docs/html", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "View generated documentation in the browser")
    public ResponseEntity<String> docsHtml(@RequestParam String path) {
        Path indexPath = memoryManager.memoryPath(repo(path)).resolve("documentation/index.html");
        if (!Files.isRegularFile(indexPath)) {
            return ResponseEntity.notFound().build();
        }
        try {
            return ResponseEntity.ok(Files.readString(indexPath));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to read documentation: " + e.getMessage());
        }
    }

    @PostMapping("/export")
    @Operation(summary = "Export .ai-memory to a directory")
    public ResponseEntity<ExportResponse> export(@Valid @RequestBody ExportRequest request) {
        Path outputDir = request.outputDir() != null && !request.outputDir().isBlank()
                ? Path.of(request.outputDir())
                : Path.of(".");
        Path exported = exportUseCase.execute(repo(request.path()), outputDir);
        return ResponseEntity.ok(new ExportResponse(exported.toString()));
    }

    @PostMapping("/ask")
    @Operation(summary = "Ask a question about the repository")
    public ResponseEntity<AskResponse> ask(@Valid @RequestBody AskRequest request) {
        AskResponse response = askUseCase.execute(
                repo(request.path()), request.question(), request.model());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/memory")
    @Operation(summary = "Get memory overview statistics")
    public ResponseEntity<MemoryOverview> memory(@RequestParam String path) {
        return memoryOverviewUseCase.execute(repo(path))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/history")
    @Operation(summary = "List recent change history snapshots")
    public ResponseEntity<List<HistorySnapshot>> history(
            @RequestParam String path,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        Path repositoryPath = repo(path);
        if (memoryManager.loadMetadata(repositoryPath).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(memoryManager.listHistorySnapshots(repositoryPath, limit));
    }

    @GetMapping("/graph")
    @Operation(summary = "Query the knowledge graph")
    public ResponseEntity<KnowledgeGraph> graph(
            @RequestParam String path,
            @RequestParam(required = false) String nodeId,
            @RequestParam(defaultValue = "1") @Min(1) @Max(10) int depth,
            @RequestParam(required = false) GraphNodeType nodeType,
            @RequestParam(required = false) String packagePrefix) {
        return graphQueryUseCase.execute(repo(path), nodeId, depth, nodeType, packagePrefix)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/graph/mermaid", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Get a Mermaid diagram for the knowledge graph")
    public ResponseEntity<String> graphMermaid(
            @RequestParam String path,
            @RequestParam(required = false) String nodeId,
            @RequestParam(defaultValue = "1") @Min(1) @Max(10) int depth,
            @RequestParam(required = false) GraphNodeType nodeType,
            @RequestParam(required = false) String packagePrefix) {
        return graphQueryUseCase.mermaid(repo(path), nodeId, depth, nodeType, packagePrefix)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/cache")
    @Operation(summary = "Clear cached entries for a repository")
    public ResponseEntity<Void> clearCache(@RequestParam String path) {
        memoryManager.clearCache(repo(path));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/memory")
    @Operation(summary = "Delete all persisted memory for a repository")
    public ResponseEntity<Void> clean(@RequestParam String path) {
        memoryManager.clean(repo(path));
        return ResponseEntity.noContent().build();
    }

    private static Path repo(String path) {
        return RepositoryPathResolver.resolve(path);
    }
}
