package com.projectmind.core.port;

import java.util.List;
import java.util.Optional;

/**
 * Port for discovering and managing ProjectMind plugins.
 */
public interface PluginRegistryPort {

    /**
     * Initializes enabled plugins with runtime context.
     */
    void initialize(PluginContext context);

    /**
     * Shuts down all loaded plugins.
     */
    void shutdown();

    /**
     * Returns all discovered plugins (including disabled).
     */
    List<ProjectMindPlugin> getPlugins();

    /**
     * Returns plugins that are enabled for this runtime.
     */
    List<ProjectMindPlugin> getEnabledPlugins();

    /**
     * Finds a plugin by name among all discovered plugins.
     */
    Optional<ProjectMindPlugin> findByName(String name);
}
