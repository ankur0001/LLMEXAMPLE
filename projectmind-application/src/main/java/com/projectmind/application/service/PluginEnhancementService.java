package com.projectmind.application.service;

import com.projectmind.core.domain.KnowledgeGraph;
import com.projectmind.core.domain.ParsedFile;
import com.projectmind.core.graph.GraphEnhancementContext;
import com.projectmind.core.port.PluginRegistryPort;
import com.projectmind.core.port.ProjectMindPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Applies enabled {@link ProjectMindPlugin} instances to a built knowledge graph.
 */
public class PluginEnhancementService {

    private static final Logger log = LoggerFactory.getLogger(PluginEnhancementService.class);

    private final PluginRegistryPort pluginRegistry;

    public PluginEnhancementService(PluginRegistryPort pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    /**
     * Runs per-file enhancement and finalize hooks for all enabled plugins.
     */
    public KnowledgeGraph enhance(KnowledgeGraph graph, List<ParsedFile> parsedFiles) {
        List<ProjectMindPlugin> plugins = pluginRegistry.getEnabledPlugins();
        if (plugins.isEmpty()) {
            return graph;
        }

        GraphEnhancementContext context = new GraphEnhancementContext(graph);
        for (ParsedFile file : parsedFiles) {
            for (ProjectMindPlugin plugin : plugins) {
                if (plugin.appliesTo(file)) {
                    plugin.enhance(context, file);
                }
            }
        }
        for (ProjectMindPlugin plugin : plugins) {
            plugin.finalizeGraph(context);
        }

        KnowledgeGraph enhanced = context.toGraph();
        log.debug("Plugin enhancement complete: {} plugins, {} nodes, {} edges",
                plugins.size(), enhanced.nodes().size(), enhanced.edges().size());
        return enhanced;
    }
}
