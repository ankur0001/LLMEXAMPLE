package com.projectmind.adapter.html;

import com.projectmind.core.domain.DocSection;
import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.ProjectMetadata;
import com.projectmind.core.domain.RepositoryFile;
import com.projectmind.core.domain.RepositoryIndex;
import com.projectmind.core.port.ConfigurationPort;
import com.projectmind.core.port.HtmlGeneratorPort;
import com.projectmind.core.port.MemoryManagerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generates a responsive, searchable HTML documentation site with dark/light mode,
 * statistics dashboard, package explorer, and Mermaid/Highlight.js rendering.
 */
@Component
public class ResponsiveHtmlGenerator implements HtmlGeneratorPort {

    private static final Logger log = LoggerFactory.getLogger(ResponsiveHtmlGenerator.class);

    private final MemoryManagerPort memoryManager;
    private final ConfigurationPort config;

    public ResponsiveHtmlGenerator(MemoryManagerPort memoryManager, ConfigurationPort config) {
        this.memoryManager = memoryManager;
        this.config = config;
    }

    @Override
    public Path generate(Path repositoryPath, ProjectMetadata metadata, List<DocSection> sections) {
        Path outputDir = memoryManager.memoryPath(repositoryPath).resolve(config.getDocsOutputDir());
        try {
            Files.createDirectories(outputDir);
            Path indexPath = outputDir.resolve("index.html");
            SiteContext context = buildContext(repositoryPath, metadata, sections);
            Files.writeString(indexPath, renderSite(context));
            log.info("Generated HTML documentation at: {}", indexPath);
            return indexPath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate HTML documentation", e);
        }
    }

    private SiteContext buildContext(Path repositoryPath, ProjectMetadata metadata, List<DocSection> sections) {
        RepositoryIndex index = memoryManager.loadIndex(repositoryPath).orElse(null);
        int graphNodes = memoryManager.loadGraph(repositoryPath).map(g -> g.nodes().size()).orElse(0);
        int graphEdges = memoryManager.loadGraph(repositoryPath).map(g -> g.edges().size()).orElse(0);
        Map<String, List<String>> packages = index != null ? groupByPackage(index.files()) : Map.of();
        Map<FileType, Integer> fileTypeCounts = index != null
                ? index.statistics().countByType()
                : Map.of();
        return new SiteContext(metadata, sections, fileTypeCounts, packages, graphNodes, graphEdges);
    }

    private static Map<String, List<String>> groupByPackage(List<RepositoryFile> files) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        files.stream()
                .sorted(Comparator.comparing(file -> file.relativePath().toString()))
                .forEach(file -> {
                    String pkg = packageNameFor(file);
                    grouped.computeIfAbsent(pkg, ignored -> new ArrayList<>())
                            .add(file.relativePath().toString());
                });
        return grouped;
    }

    private static String packageNameFor(RepositoryFile file) {
        String path = file.relativePath().toString().replace('\\', '/');
        if (file.fileType() == FileType.JAVA || file.fileType() == FileType.KOTLIN) {
            String javaPrefix = "src/main/java/";
            int srcIdx = path.indexOf(javaPrefix);
            if (srcIdx >= 0) {
                String after = path.substring(srcIdx + javaPrefix.length());
                int lastSlash = after.lastIndexOf('/');
                if (lastSlash > 0) {
                    return after.substring(0, lastSlash).replace('/', '.');
                }
            }
            String kotlinPrefix = "src/main/kotlin/";
            int kotlinIdx = path.indexOf(kotlinPrefix);
            if (kotlinIdx >= 0) {
                String after = path.substring(kotlinIdx + kotlinPrefix.length());
                int lastSlash = after.lastIndexOf('/');
                if (lastSlash > 0) {
                    return after.substring(0, lastSlash).replace('/', '.');
                }
            }
        }
        int slash = path.lastIndexOf('/');
        return slash > 0 ? path.substring(0, slash) : "(root)";
    }

    private String renderSite(SiteContext ctx) {
        var sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\" data-theme=\"light\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>").append(escape(ctx.metadata().name())).append(" - ProjectMind</title>\n");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/highlight.js@11.9.0/styles/github.min.css\" id=\"hljs-light\">\n");
        sb.append("<link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/highlight.js@11.9.0/styles/github-dark.min.css\" id=\"hljs-dark\" disabled>\n");
        sb.append("<style>").append(SiteStyles.CSS).append("</style>\n");
        sb.append("</head>\n<body>\n");
        sb.append("<div class=\"layout\">\n");
        appendSidebar(sb, ctx);
        sb.append("<div class=\"main\">\n");
        appendTopBar(sb, ctx);
        sb.append("<div class=\"breadcrumb\" id=\"breadcrumb\">Home</div>\n");
        appendDashboard(sb, ctx);
        appendSections(sb, ctx);
        sb.append("<footer>Generated by ProjectMind</footer>\n");
        sb.append("</div></div>\n");
        sb.append("<script>").append(SiteScripts.JS).append("</script>\n");
        sb.append("<script src=\"https://cdn.jsdelivr.net/npm/highlight.js@11.9.0/lib/highlight.min.js\"></script>\n");
        sb.append("<script type=\"module\">\n");
        sb.append("  import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs';\n");
        sb.append("  window.initMermaid = () => mermaid.initialize({ startOnLoad: false, theme: document.documentElement.dataset.theme === 'dark' ? 'dark' : 'neutral' });\n");
        sb.append("  initMermaid();\n");
        sb.append("  document.addEventListener('DOMContentLoaded', () => { hljs.highlightAll(); mermaid.run(); });\n");
        sb.append("</script>\n");
        sb.append("</body></html>");
        return sb.toString();
    }

    private void appendSidebar(StringBuilder sb, SiteContext ctx) {
        sb.append("<aside class=\"sidebar\" id=\"sidebar\">\n");
        sb.append("<div class=\"sidebar-header\"><strong>").append(escape(ctx.metadata().name())).append("</strong></div>\n");
        sb.append("<nav class=\"section-nav\" id=\"section-nav\">\n");
        for (DocSection section : ctx.sections()) {
            String anchor = anchor(section);
            sb.append("<a href=\"#").append(anchor).append("\" data-section=\"")
                    .append(anchor).append("\">")
                    .append(escape(section.title())).append("</a>\n");
        }
        sb.append("</nav>\n");
        sb.append("<div class=\"package-explorer\">\n");
        sb.append("<h3>Packages</h3>\n");
        if (ctx.packages().isEmpty()) {
            sb.append("<p class=\"muted\">No packages indexed.</p>\n");
        } else {
            sb.append("<ul class=\"package-tree\">\n");
            ctx.packages().entrySet().stream().limit(40).forEach(entry -> {
                sb.append("<li><details><summary>")
                        .append(escape(entry.getKey()))
                        .append(" (").append(entry.getValue().size()).append(")</summary><ul>");
                entry.getValue().stream().limit(8).forEach(file ->
                        sb.append("<li class=\"file-item\">").append(escape(file)).append("</li>"));
                if (entry.getValue().size() > 8) {
                    sb.append("<li class=\"muted\">+ ").append(entry.getValue().size() - 8).append(" more</li>");
                }
                sb.append("</ul></details></li>\n");
            });
            sb.append("</ul>\n");
        }
        sb.append("</div></aside>\n");
    }

    private void appendTopBar(StringBuilder sb, SiteContext ctx) {
        sb.append("<header class=\"topbar\">\n");
        sb.append("<button type=\"button\" class=\"icon-btn\" id=\"sidebar-toggle\" aria-label=\"Toggle sidebar\">☰</button>\n");
        sb.append("<input type=\"search\" id=\"search\" placeholder=\"Search documentation...\" aria-label=\"Search\">\n");
        sb.append("<div class=\"topbar-actions\">\n");
        sb.append("<button type=\"button\" class=\"btn\" id=\"expand-all\">Expand all</button>\n");
        sb.append("<button type=\"button\" class=\"btn\" id=\"collapse-all\">Collapse all</button>\n");
        sb.append("<button type=\"button\" class=\"icon-btn\" id=\"theme-toggle\" aria-label=\"Toggle theme\">🌓</button>\n");
        sb.append("</div></header>\n");
    }

    private void appendDashboard(StringBuilder sb, SiteContext ctx) {
        sb.append("<section class=\"dashboard\" id=\"dashboard\">\n");
        sb.append("<h2>Statistics</h2>\n");
        sb.append("<div class=\"stats-grid\">\n");
        statCard(sb, "Files", String.valueOf(ctx.metadata().totalFiles()));
        statCard(sb, "Status", ctx.metadata().status().name());
        statCard(sb, "Graph nodes", String.valueOf(ctx.graphNodes()));
        statCard(sb, "Graph edges", String.valueOf(ctx.graphEdges()));
        statCard(sb, "Packages", String.valueOf(ctx.packages().size()));
        statCard(sb, "Last scanned", ctx.metadata().lastScannedAt().toString());
        sb.append("</div>\n");
        if (!ctx.fileTypeCounts().isEmpty()) {
            sb.append("<div class=\"type-breakdown\">\n");
            ctx.fileTypeCounts().entrySet().stream()
                    .sorted(Map.Entry.<FileType, Integer>comparingByValue().reversed())
                    .forEach(entry -> sb.append("<span class=\"chip\">")
                            .append(entry.getKey()).append(": ").append(entry.getValue()).append("</span>\n"));
            sb.append("</div>\n");
        }
        sb.append("</section>\n");
    }

    private void statCard(StringBuilder sb, String label, String value) {
        sb.append("<div class=\"stat-card\"><div class=\"stat-label\">")
                .append(escape(label))
                .append("</div><div class=\"stat-value\">")
                .append(escape(value))
                .append("</div></div>\n");
    }

    private void appendSections(StringBuilder sb, SiteContext ctx) {
        sb.append("<main class=\"content\" id=\"content\">\n");
        for (DocSection section : ctx.sections()) {
            String anchor = anchor(section);
            sb.append("<article class=\"doc-section\" id=\"").append(anchor)
                    .append("\" data-title=\"").append(escape(section.title().toLowerCase(Locale.ROOT)))
                    .append("\">\n");
            sb.append("<details open class=\"section-panel\">\n");
            sb.append("<summary><h2>").append(escape(section.title())).append("</h2></summary>\n");
            sb.append("<div class=\"section-body markdown\">")
                    .append(MarkdownRenderer.toHtml(section.markdownContent()))
                    .append("</div>\n");
            if (section.mermaidDiagram() != null && !section.mermaidDiagram().isBlank()) {
                sb.append("<pre class=\"mermaid\">")
                        .append(section.mermaidDiagram())
                        .append("</pre>\n");
            }
            sb.append("</details></article>\n");
        }
        sb.append("</main>\n");
    }

    private static String anchor(DocSection section) {
        return section.type().name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private record SiteContext(
            ProjectMetadata metadata,
            List<DocSection> sections,
            Map<FileType, Integer> fileTypeCounts,
            Map<String, List<String>> packages,
            int graphNodes,
            int graphEdges) {
    }

    private static final class SiteStyles {
        private static final String CSS = """
                :root {
                  --bg: #f7f8fb; --surface: #ffffff; --text: #1f2937; --muted: #6b7280;
                  --border: #e5e7eb; --accent: #2563eb; --sidebar-width: 280px;
                }
                html[data-theme='dark'] {
                  --bg: #0f172a; --surface: #111827; --text: #e5e7eb; --muted: #94a3b8;
                  --border: #334155; --accent: #60a5fa;
                }
                * { box-sizing: border-box; }
                body { margin: 0; font-family: Inter, system-ui, sans-serif; background: var(--bg); color: var(--text); }
                .layout { display: flex; min-height: 100vh; }
                .sidebar {
                  width: var(--sidebar-width); background: var(--surface); border-right: 1px solid var(--border);
                  position: sticky; top: 0; height: 100vh; overflow: auto; padding: 1rem; flex-shrink: 0;
                }
                .sidebar-header { margin-bottom: 1rem; font-size: 1.1rem; }
                .section-nav { display: flex; flex-direction: column; gap: .35rem; margin-bottom: 1.5rem; }
                .section-nav a {
                  color: var(--text); text-decoration: none; padding: .35rem .5rem; border-radius: 6px; font-size: .92rem;
                }
                .section-nav a:hover, .section-nav a.active { background: color-mix(in srgb, var(--accent) 12%, transparent); color: var(--accent); }
                .package-explorer h3 { margin: 0 0 .5rem; font-size: .95rem; }
                .package-tree, .package-tree ul { list-style: none; padding-left: .75rem; margin: .25rem 0; }
                .package-tree summary { cursor: pointer; font-size: .85rem; }
                .file-item, .muted { font-size: .78rem; color: var(--muted); word-break: break-all; }
                .main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
                .topbar {
                  position: sticky; top: 0; z-index: 10; display: flex; gap: .75rem; align-items: center;
                  padding: .75rem 1rem; background: var(--surface); border-bottom: 1px solid var(--border);
                }
                #search { flex: 1; padding: .55rem .75rem; border: 1px solid var(--border); border-radius: 8px; background: var(--bg); color: var(--text); }
                .topbar-actions { display: flex; gap: .5rem; align-items: center; }
                .btn, .icon-btn {
                  border: 1px solid var(--border); background: var(--surface); color: var(--text);
                  border-radius: 8px; padding: .45rem .65rem; cursor: pointer;
                }
                .breadcrumb { padding: .75rem 1rem 0; color: var(--muted); font-size: .9rem; }
                .dashboard, .content { padding: 1rem; }
                .stats-grid {
                  display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: .75rem; margin-top: .75rem;
                }
                .stat-card { background: var(--surface); border: 1px solid var(--border); border-radius: 10px; padding: .85rem; }
                .stat-label { color: var(--muted); font-size: .8rem; }
                .stat-value { font-size: 1.1rem; font-weight: 600; margin-top: .25rem; word-break: break-word; }
                .type-breakdown { display: flex; flex-wrap: wrap; gap: .5rem; margin-top: 1rem; }
                .chip { background: var(--surface); border: 1px solid var(--border); border-radius: 999px; padding: .25rem .65rem; font-size: .8rem; }
                .doc-section { margin-bottom: 1rem; }
                .section-panel { background: var(--surface); border: 1px solid var(--border); border-radius: 12px; padding: .5rem 1rem 1rem; }
                .section-panel summary { cursor: pointer; list-style: none; }
                .section-panel summary::-webkit-details-marker { display: none; }
                .section-panel h2 { display: inline; margin: .5rem 0; font-size: 1.15rem; }
                .section-body { margin-top: .75rem; line-height: 1.6; }
                .markdown table { width: 100%; border-collapse: collapse; margin: 1rem 0; font-size: .92rem; }
                .markdown th, .markdown td { border: 1px solid var(--border); padding: .5rem .65rem; text-align: left; }
                .markdown pre { background: #0f172a; color: #e2e8f0; padding: 1rem; border-radius: 8px; overflow: auto; }
                .markdown code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
                .markdown blockquote { border-left: 3px solid var(--accent); margin: .75rem 0; padding-left: .75rem; color: var(--muted); }
                .mermaid { background: var(--surface); border: 1px solid var(--border); border-radius: 8px; padding: 1rem; overflow: auto; }
                footer { padding: 1rem; color: var(--muted); font-size: .85rem; }
                .hidden { display: none !important; }
                @media (max-width: 900px) {
                  .sidebar { position: fixed; left: 0; transform: translateX(-100%); transition: transform .2s ease; z-index: 20; }
                  .sidebar.open { transform: translateX(0); }
                }
                """;
    }

    private static final class SiteScripts {
        private static final String JS = """
                const root = document.documentElement;
                const sidebar = document.getElementById('sidebar');
                const search = document.getElementById('search');
                const breadcrumb = document.getElementById('breadcrumb');
                const sections = [...document.querySelectorAll('.doc-section')];
                const navLinks = [...document.querySelectorAll('.section-nav a')];

                document.getElementById('theme-toggle').addEventListener('click', () => {
                  const next = root.dataset.theme === 'dark' ? 'light' : 'dark';
                  root.dataset.theme = next;
                  document.getElementById('hljs-light').disabled = next === 'dark';
                  document.getElementById('hljs-dark').disabled = next !== 'dark';
                  if (window.initMermaid) { initMermaid(); mermaid.run(); }
                });

                document.getElementById('sidebar-toggle').addEventListener('click', () => {
                  sidebar.classList.toggle('open');
                });

                document.getElementById('expand-all').addEventListener('click', () => {
                  document.querySelectorAll('.section-panel').forEach(p => p.open = true);
                });

                document.getElementById('collapse-all').addEventListener('click', () => {
                  document.querySelectorAll('.section-panel').forEach(p => p.open = false);
                });

                search.addEventListener('input', () => {
                  const q = search.value.trim().toLowerCase();
                  sections.forEach(section => {
                    const text = (section.dataset.title + ' ' + section.textContent).toLowerCase();
                    section.classList.toggle('hidden', q && !text.includes(q));
                  });
                });

                function setActiveNav(id) {
                  navLinks.forEach(link => link.classList.toggle('active', link.dataset.section === id));
                  const title = navLinks.find(link => link.dataset.section === id)?.textContent?.trim();
                  breadcrumb.textContent = title ? 'Home > ' + title : 'Home';
                }

                navLinks.forEach(link => link.addEventListener('click', () => {
                  setActiveNav(link.dataset.section);
                  sidebar.classList.remove('open');
                }));

                const observer = new IntersectionObserver(entries => {
                  const visible = entries.filter(e => e.isIntersecting).sort((a,b) => b.intersectionRatio - a.intersectionRatio)[0];
                  if (visible) setActiveNav(visible.target.id);
                }, { rootMargin: '-20% 0px -60% 0px', threshold: [0.1, 0.4] });

                sections.forEach(section => observer.observe(section));
                if (sections[0]) setActiveNav(sections[0].id);
                """;
    }
}
