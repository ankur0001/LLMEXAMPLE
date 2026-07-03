package com.projectmind.adapter.docs;

import com.projectmind.core.domain.DocSection;
import com.projectmind.core.domain.DocSectionType;
import com.projectmind.core.domain.FileSummary;
import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.GraphEdge;
import com.projectmind.core.domain.GraphEdgeType;
import com.projectmind.core.domain.GraphNode;
import com.projectmind.core.domain.GraphNodeType;
import com.projectmind.core.domain.KnowledgeGraph;
import com.projectmind.core.domain.ProjectMetadata;
import com.projectmind.core.domain.RepositoryFile;
import com.projectmind.core.domain.RepositoryIndex;
import com.projectmind.core.port.DocumentationGeneratorPort;
import com.projectmind.core.port.KnowledgeGraphPort;
import com.projectmind.core.port.MemoryManagerPort;
import com.projectmind.core.port.OllamaClientPort;
import com.projectmind.core.prompt.OllamaPromptTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Template-based documentation generator with optional Ollama narrative enrichment.
 */
@Component
public class TemplateDocumentationGenerator implements DocumentationGeneratorPort {

    private static final Logger log = LoggerFactory.getLogger(TemplateDocumentationGenerator.class);
    private static final Set<DocSectionType> NARRATIVE_SECTIONS = EnumSet.of(
            DocSectionType.REPOSITORY_OVERVIEW,
            DocSectionType.ARCHITECTURE,
            DocSectionType.DEVELOPER_GUIDE);

    private final MemoryManagerPort memoryManager;
    private final KnowledgeGraphPort knowledgeGraphPort;
    private final OllamaClientPort ollamaClient;

    public TemplateDocumentationGenerator(
            MemoryManagerPort memoryManager,
            KnowledgeGraphPort knowledgeGraphPort,
            OllamaClientPort ollamaClient) {
        this.memoryManager = memoryManager;
        this.knowledgeGraphPort = knowledgeGraphPort;
        this.ollamaClient = ollamaClient;
    }

    @Override
    public List<DocSection> generateAll(
            Path repositoryPath,
            ProjectMetadata metadata,
            RepositoryIndex index,
            KnowledgeGraph graph) {
        log.info("Generating documentation for {}", metadata.name());
        DocumentationContext context = buildContext(repositoryPath, metadata, index, graph);
        return Arrays.stream(DocSectionType.values())
                .map(type -> buildSection(type, context))
                .toList();
    }

    @Override
    public List<DocSection> regenerateAffected(
            Path repositoryPath,
            ProjectMetadata metadata,
            RepositoryIndex index,
            KnowledgeGraph graph,
            List<String> changedFiles) {
        DocumentationContext context = buildContext(repositoryPath, metadata, index, graph);
        Set<DocSectionType> affected = new LinkedHashSet<>();
        for (String changedFile : changedFiles) {
            affected.addAll(context.sectionsAffectedBy(changedFile));
        }
        log.info("Regenerating {} documentation sections for {} changed files",
                affected.size(), changedFiles.size());
        return affected.stream()
                .map(type -> buildSection(type, context))
                .toList();
    }

    private DocumentationContext buildContext(
            Path repositoryPath,
            ProjectMetadata metadata,
            RepositoryIndex index,
            KnowledgeGraph graph) {
        Map<String, FileSummary> summaries = new HashMap<>();
        for (RepositoryFile file : index.files()) {
            memoryManager.loadSummary(repositoryPath, file.relativePath().toString())
                    .ifPresent(summary -> summaries.put(summary.relativePath(), summary));
        }
        return new DocumentationContext(repositoryPath, metadata, index, graph, summaries);
    }

    private DocSection buildSection(DocSectionType type, DocumentationContext ctx) {
        String title = CrossLinkRegistry.formatTitle(type);
        String markdown = switch (type) {
            case REPOSITORY_OVERVIEW -> buildRepositoryOverview(ctx);
            case ARCHITECTURE -> buildArchitecture(ctx);
            case TECHNOLOGY_STACK -> buildTechnologyStack(ctx);
            case STARTUP_FLOW -> buildStartupFlow(ctx);
            case CONTROLLERS -> buildNodeSection(ctx, GraphNodeType.CONTROLLER, "Controllers");
            case SERVICES -> buildNodeSection(ctx, GraphNodeType.SERVICE, "Services");
            case REPOSITORIES -> buildNodeSection(ctx, GraphNodeType.REPOSITORY, "Repositories");
            case ENTITIES -> buildNodeSection(ctx, GraphNodeType.ENTITY, "Entities");
            case SECURITY -> buildSecurity(ctx);
            case CONFIGURATION -> buildConfiguration(ctx);
            case API_DOCUMENTATION -> buildApiDocumentation(ctx);
            case DATABASE -> buildDatabase(ctx);
            case UTILITIES -> buildUtilities(ctx);
            case SCHEDULERS -> buildSchedulers(ctx);
            case EXTERNAL_INTEGRATIONS -> buildExternalIntegrations(ctx);
            case DESIGN_PATTERNS -> buildDesignPatterns(ctx);
            case DEPENDENCY_GRAPH -> buildDependencyGraph(ctx);
            case SEQUENCE_DIAGRAMS -> buildSequenceDiagrams(ctx);
            case CODE_QUALITY -> buildCodeQuality(ctx);
            case DEVELOPER_GUIDE -> buildDeveloperGuide(ctx);
            case GLOSSARY -> buildGlossary(ctx);
            case SOURCE_FILES -> buildSourceFiles(ctx);
        };
        String mermaid = switch (type) {
            case DEPENDENCY_GRAPH -> knowledgeGraphPort.toMermaidDiagram(ctx.graph());
            case SEQUENCE_DIAGRAMS -> SequenceDiagramBuilder.build(ctx.graph());
            default -> null;
        };
        return new DocSection(type, title, markdown + CrossLinkRegistry.seeAlso(type), mermaid);
    }

    private String buildRepositoryOverview(DocumentationContext ctx) {
        var sb = new StringBuilder();
        sb.append("# Repository Overview\n\n");
        sb.append("**Project:** ").append(ctx.metadata().name()).append('\n');
        sb.append("**Status:** ").append(ctx.metadata().status()).append('\n');
        sb.append("**Total files:** ").append(ctx.metadata().totalFiles()).append('\n');
        sb.append("**Last scanned:** ").append(ctx.metadata().lastScannedAt()).append("\n\n");
        sb.append("## Statistics\n\n");
        ctx.index().statistics().countByType().forEach((type, count) ->
                sb.append("- **").append(type).append(":** ").append(count).append('\n'));
        sb.append("\n## Summary\n\n");
        sb.append(narrativeOrFallback(
                DocSectionType.REPOSITORY_OVERVIEW,
                ctx,
                fallbackOverview(ctx)));
        return sb.toString();
    }

    private String fallbackOverview(DocumentationContext ctx) {
        long controllers = ctx.nodesOfType(GraphNodeType.CONTROLLER).size();
        long services = ctx.nodesOfType(GraphNodeType.SERVICE).size();
        long repositories = ctx.nodesOfType(GraphNodeType.REPOSITORY).size();
        return "This repository contains "
                + ctx.metadata().totalFiles()
                + " indexed files with "
                + controllers + " controllers, "
                + services + " services, and "
                + repositories + " repositories detected in the knowledge graph.";
    }

    private String buildArchitecture(DocumentationContext ctx) {
        var sb = new StringBuilder("# Architecture\n\n");
        sb.append("The knowledge graph contains **")
                .append(ctx.graph().nodes().size())
                .append("** nodes and **")
                .append(ctx.graph().edges().size())
                .append("** edges.\n\n## Layers\n\n");
        appendLayerTable(sb, ctx);
        sb.append("\n## Narrative\n\n");
        sb.append(narrativeOrFallback(
                DocSectionType.ARCHITECTURE,
                ctx,
                "The project follows a layered structure inferred from Spring stereotypes and dependency edges."));
        return sb.toString();
    }

    private void appendLayerTable(StringBuilder sb, DocumentationContext ctx) {
        ctx.nodeCountsByType().entrySet().stream()
                .filter(entry -> isArchitectureLayer(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sb.append("- **")
                        .append(entry.getKey())
                        .append(":** ")
                        .append(entry.getValue())
                        .append('\n'));
    }

    private boolean isArchitectureLayer(GraphNodeType type) {
        return type == GraphNodeType.CONTROLLER
                || type == GraphNodeType.SERVICE
                || type == GraphNodeType.REPOSITORY
                || type == GraphNodeType.ENTITY
                || type == GraphNodeType.CONFIGURATION
                || type == GraphNodeType.FILTER;
    }

    private String buildTechnologyStack(DocumentationContext ctx) {
        var sb = new StringBuilder("# Technology Stack\n\n## Detected file types\n\n");
        ctx.index().statistics().countByType().forEach((type, count) ->
                sb.append("- ").append(type).append(": ").append(count).append(" files\n"));

        List<GraphNode> modules = ctx.nodesOfType(GraphNodeType.MODULE);
        if (!modules.isEmpty()) {
            sb.append("\n## Maven/Gradle dependencies\n\n");
            ctx.edgesOfType(GraphEdgeType.DEPENDS_ON).stream()
                    .limit(30)
                    .forEach(edge -> sb.append("- ").append(edge.label()).append('\n'));
        }

        if (ctx.filesOfType(FileType.DOCKER).size() > 0) {
            sb.append("\n- Docker configuration detected\n");
        }
        if (ctx.filesOfType(FileType.KUBERNETES).size() > 0) {
            sb.append("- Kubernetes manifests detected\n");
        }
        return sb.toString();
    }

    private String buildStartupFlow(DocumentationContext ctx) {
        var sb = new StringBuilder("# Startup Flow\n\n");
        sb.append("Typical Spring Boot startup involves loading configuration, creating the application context, ");
        sb.append("and wiring beans detected in the knowledge graph.\n\n## Configuration files\n\n");
        List.of(FileType.YAML, FileType.PROPERTIES).forEach(type ->
                ctx.filesOfType(type).forEach(file ->
                        sb.append("- `").append(file.relativePath()).append("`\n")));
        List<GraphNode> configs = ctx.nodesOfType(GraphNodeType.CONFIGURATION);
        if (!configs.isEmpty()) {
            sb.append("\n## Configuration classes\n\n");
            configs.forEach(node -> sb.append("- **")
                    .append(node.name())
                    .append("** — `")
                    .append(node.sourceFile())
                    .append("`\n"));
        }
        return sb.toString();
    }

    private String buildNodeSection(DocumentationContext ctx, GraphNodeType type, String heading) {
        List<GraphNode> nodes = ctx.nodesOfType(type);
        var sb = new StringBuilder("# ").append(heading).append("\n\n");
        if (nodes.isEmpty()) {
            sb.append("No ").append(heading.toLowerCase(Locale.ROOT)).append(" were detected.\n");
            return sb.toString();
        }
        sb.append("| Name | Package | Source file |\n|------|---------|-------------|\n");
        for (GraphNode node : nodes) {
            sb.append("| **").append(node.name()).append("** | ")
                    .append(node.packageName().isBlank() ? "—" : node.packageName())
                    .append(" | `").append(node.sourceFile()).append("` |\n");
            String summary = ctx.summaryFor(node.sourceFile());
            if (!summary.isBlank()) {
                sb.append("\n> ").append(summary.replace('\n', ' ')).append("\n\n");
            }
        }
        return sb.toString();
    }

    private String buildSecurity(DocumentationContext ctx) {
        var sb = new StringBuilder("# Security\n\n");
        List<GraphNode> filters = ctx.nodesOfType(GraphNodeType.FILTER);
        if (filters.isEmpty()) {
            sb.append("No security filters or `@EnableWebSecurity` configuration were detected.\n");
            return sb.toString();
        }
        sb.append("## Security components\n\n");
        filters.forEach(node -> sb.append("- **")
                .append(node.name())
                .append("** — `")
                .append(node.sourceFile())
                .append("`\n"));
        sb.append("\n## Secured resources\n\n");
        ctx.edgesOfType(GraphEdgeType.SECURES).forEach(edge ->
                sb.append("- ").append(edge.label()).append('\n'));
        return sb.toString();
    }

    private String buildConfiguration(DocumentationContext ctx) {
        var sb = new StringBuilder("# Configuration\n\n");
        List.of(FileType.YAML, FileType.PROPERTIES, FileType.KUBERNETES).forEach(type -> {
            List<RepositoryFile> files = ctx.filesOfType(type);
            if (!files.isEmpty()) {
                sb.append("## ").append(type).append(" files\n\n");
                files.forEach(file -> sb.append("- `").append(file.relativePath()).append("`\n"));
            }
        });
        ctx.edgesOfType(GraphEdgeType.CONFIGURES).stream()
                .limit(25)
                .forEach(edge -> sb.append("- Configures **")
                        .append(edge.label())
                        .append("**\n"));
        return sb.toString();
    }

    private String buildApiDocumentation(DocumentationContext ctx) {
        var sb = new StringBuilder("# API Documentation\n\n");
        List<GraphNode> controllers = ctx.nodesOfType(GraphNodeType.CONTROLLER);
        if (controllers.isEmpty()) {
            sb.append("No REST controllers were detected.\n");
            return sb.toString();
        }
        sb.append("REST endpoints are inferred from `@RestController` / `@Controller` types.\n\n");
        for (GraphNode controller : controllers) {
            sb.append("## ").append(controller.name()).append("\n\n");
            sb.append("- **Source:** `").append(controller.sourceFile()).append("`\n");
            sb.append("- **Package:** ").append(controller.packageName()).append("\n");
            String summary = ctx.summaryFor(controller.sourceFile());
            if (!summary.isBlank()) {
                sb.append("- **Summary:** ").append(summary).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private String buildDatabase(DocumentationContext ctx) {
        var sb = new StringBuilder("# Database\n\n");
        List<GraphNode> databases = ctx.nodesOfType(GraphNodeType.DATABASE);
        if (databases.isEmpty()) {
            sb.append("No database tables or SQL artifacts were detected.\n");
            return sb.toString();
        }
        sb.append("| Table / Resource | Source |\n|------------------|--------|\n");
        databases.forEach(node -> sb.append("| **")
                .append(node.name())
                .append("** | `")
                .append(node.sourceFile())
                .append("` |\n"));
        ctx.edgesOfType(GraphEdgeType.MAPS_TO).forEach(edge ->
                sb.append("\n- Entity maps to **").append(edge.label()).append("**\n"));
        return sb.toString();
    }

    private String buildUtilities(DocumentationContext ctx) {
        var sb = new StringBuilder("# Utilities\n\n");
        List<GraphNode> utilities = ctx.graph().nodes().stream()
                .filter(node -> node.type() == GraphNodeType.CLASS || node.type() == GraphNodeType.INTERFACE)
                .filter(node -> !isSpringLayer(node.type()))
                .sorted(java.util.Comparator.comparing(GraphNode::name))
                .limit(40)
                .toList();
        if (utilities.isEmpty()) {
            sb.append("No standalone utility types were identified.\n");
            return sb.toString();
        }
        utilities.forEach(node -> sb.append("- **")
                .append(node.name())
                .append("** — `")
                .append(node.sourceFile())
                .append("`\n"));
        return sb.toString();
    }

    private String buildSchedulers(DocumentationContext ctx) {
        var sb = new StringBuilder("# Schedulers\n\n");
        List<String> schedulers = ctx.graph().nodes().stream()
                .filter(node -> ctx.summaryFor(node.sourceFile()).toLowerCase(Locale.ROOT).contains("scheduled")
                        || node.name().toLowerCase(Locale.ROOT).contains("scheduler"))
                .map(node -> node.name() + " (`" + node.sourceFile() + "`)")
                .distinct()
                .toList();
        if (schedulers.isEmpty()) {
            sb.append("No `@Scheduled` tasks were detected from available summaries.\n");
        } else {
            schedulers.forEach(item -> sb.append("- ").append(item).append('\n'));
        }
        return sb.toString();
    }

    private String buildExternalIntegrations(DocumentationContext ctx) {
        var sb = new StringBuilder("# External Integrations\n\n");
        ctx.edgesOfType(GraphEdgeType.DEPENDS_ON).stream()
                .filter(edge -> edge.label() != null && !edge.label().contains("->"))
                .limit(30)
                .forEach(edge -> sb.append("- ").append(edge.label()).append('\n'));
        if (ctx.filesOfType(FileType.KUBERNETES).size() > 0) {
            sb.append("\n## Kubernetes\n\n");
            ctx.filesOfType(FileType.KUBERNETES)
                    .forEach(file -> sb.append("- `").append(file.relativePath()).append("`\n"));
        }
        return sb.toString();
    }

    private String buildDesignPatterns(DocumentationContext ctx) {
        var sb = new StringBuilder("# Design Patterns\n\n");
        long injectionEdges = ctx.edgesOfType(GraphEdgeType.INJECTS).size();
        long layerEdges = ctx.edgesOfType(GraphEdgeType.DEPENDS_ON).size();
        sb.append("Patterns inferred from the dependency graph:\n\n");
        if (injectionEdges > 0) {
            sb.append("- **Dependency Injection** — ").append(injectionEdges).append(" injection edges\n");
        }
        if (layerEdges > 0) {
            sb.append("- **Layered Architecture** — ").append(layerEdges).append(" layer flow edges\n");
        }
        if (ctx.nodesOfType(GraphNodeType.REPOSITORY).size() > 0) {
            sb.append("- **Repository pattern** — data access abstracted behind repository interfaces\n");
        }
        if (ctx.nodesOfType(GraphNodeType.ENTITY).size() > 0) {
            sb.append("- **Domain model / JPA entities** — persistence-mapped types detected\n");
        }
        return sb.toString();
    }

    private String buildDependencyGraph(DocumentationContext ctx) {
        return "# Dependency Graph\n\n"
                + "The knowledge graph visualizes relationships between packages, types, configuration, "
                + "and external modules.\n\n"
                + "- **Nodes:** " + ctx.graph().nodes().size() + "\n"
                + "- **Edges:** " + ctx.graph().edges().size() + "\n\n"
                + "See the diagram below.\n";
    }

    private String buildSequenceDiagrams(DocumentationContext ctx) {
        var sb = new StringBuilder("# Sequence Diagrams\n\n");
        sb.append("Sequence flows inferred from controller → service → repository interactions.\n\n");
        if (SequenceDiagramBuilder.build(ctx.graph()).isBlank()) {
            sb.append("*No layer interaction flows were detected.*\n");
        } else {
            sb.append("See the diagram below.\n");
        }
        return sb.toString();
    }

    private String buildCodeQuality(DocumentationContext ctx) {
        return "# Code Quality\n\n"
                + "- **Total files:** " + ctx.metadata().totalFiles() + "\n"
                + "- **Total size:** " + formatBytes(ctx.index().statistics().totalBytes()) + "\n"
                + "- **Graph coverage:** " + ctx.graph().nodes().size() + " nodes from "
                + ctx.index().files().size() + " indexed files\n"
                + "- **Summaries available:** " + ctx.index().files().stream()
                .filter(file -> !ctx.summaryFor(file.relativePath().toString()).isBlank())
                .count() + " files\n";
    }

    private String buildDeveloperGuide(DocumentationContext ctx) {
        var sb = new StringBuilder("# Developer Guide\n\n");
        sb.append("## Suggested reading order\n\n");
        sb.append("1. [Repository Overview](#repository-overview)\n");
        sb.append("2. [Architecture](#architecture)\n");
        sb.append("3. [Controllers](#controllers) → [Services](#services) → [Repositories](#repositories)\n");
        sb.append("4. [Configuration](#configuration)\n");
        sb.append("5. [Glossary](#glossary)\n\n");
        sb.append("6. [Source files](#source-files)\n\n");
        sb.append("## Narrative\n\n");
        sb.append(narrativeOrFallback(
                DocSectionType.DEVELOPER_GUIDE,
                ctx,
                "Start with the architecture overview, then explore layer-specific sections linked above."));
        return sb.toString();
    }

    private String buildSourceFiles(DocumentationContext ctx) {
        var sb = new StringBuilder("# Source Files\n\n");
        sb.append("Per-file understanding generated from indexed sources. ")
                .append("Each entry summarizes purpose, key types, and responsibilities.\n\n");

        Map<String, List<RepositoryFile>> grouped = ctx.index().files().stream()
                .sorted(java.util.Comparator.comparing(file -> file.relativePath().toString()))
                .collect(Collectors.groupingBy(
                        file -> packageGroupFor(file),
                        java.util.LinkedHashMap::new,
                        Collectors.toList()));

        for (Map.Entry<String, List<RepositoryFile>> entry : grouped.entrySet()) {
            sb.append("## ").append(entry.getKey()).append("\n\n");
            for (RepositoryFile file : entry.getValue()) {
                sb.append("### `").append(file.relativePath()).append("`\n\n");
                sb.append("- **Type:** ").append(file.fileType()).append('\n');
                String summary = ctx.summaryFor(file.relativePath().toString());
                if (!summary.isBlank()) {
                    sb.append("- **Understanding:** ").append(summary).append("\n\n");
                } else {
                    sb.append("- **Understanding:** *Summary pending or not applicable for this file type.*\n\n");
                }
            }
        }
        return sb.toString();
    }

    private static String packageGroupFor(RepositoryFile file) {
        String path = file.relativePath().toString().replace('\\', '/');
        int slash = path.lastIndexOf('/');
        return slash > 0 ? path.substring(0, slash) : "(root)";
    }

    private String buildGlossary(DocumentationContext ctx) {
        List<GlossaryExtractor.GlossaryEntry> entries = GlossaryExtractor.extract(ctx.graph(), ctx.summaries());
        var sb = new StringBuilder("# Glossary\n\n");
        if (entries.isEmpty()) {
            sb.append("No glossary terms extracted yet.\n");
            return sb.toString();
        }
        sb.append("| Term | Definition |\n|------|------------|\n");
        entries.stream().limit(100).forEach(entry -> sb.append("| **")
                .append(entry.term())
                .append("** | ")
                .append(entry.definition())
                .append(" |\n"));
        return sb.toString();
    }

    private String narrativeOrFallback(DocSectionType type, DocumentationContext ctx, String fallback) {
        if (!NARRATIVE_SECTIONS.contains(type)) {
            return fallback;
        }
        try {
            String prompt = switch (type) {
                case REPOSITORY_OVERVIEW -> OllamaPromptTemplates.repositoryOverview(
                        ctx.metadata().name(), ctx.buildContextBundle());
                case ARCHITECTURE -> OllamaPromptTemplates.architectureNarrative(
                        ctx.metadata().name(), ctx.buildContextBundle());
                case DEVELOPER_GUIDE -> OllamaPromptTemplates.developerGuide(
                        ctx.metadata().name(), ctx.buildContextBundle());
                default -> null;
            };
            if (prompt == null) {
                return fallback;
            }
            String response = ollamaClient.complete(prompt);
            if (response == null || response.isBlank()) {
                return fallback;
            }
            return response.trim();
        } catch (Exception ex) {
            log.debug("Ollama narrative unavailable for {}: {}", type, ex.getMessage());
            return fallback;
        }
    }

    private static boolean isSpringLayer(GraphNodeType type) {
        return type == GraphNodeType.CONTROLLER
                || type == GraphNodeType.SERVICE
                || type == GraphNodeType.REPOSITORY;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
