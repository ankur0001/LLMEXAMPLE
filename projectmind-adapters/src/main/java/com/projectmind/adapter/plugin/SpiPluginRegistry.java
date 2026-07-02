package com.projectmind.adapter.plugin;

import com.projectmind.adapter.config.ProjectMindProperties;
import com.projectmind.core.port.PluginContext;
import com.projectmind.core.port.PluginRegistryPort;
import com.projectmind.core.port.ProjectMindPlugin;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Discovers plugins via Java SPI and applies enable/disable configuration.
 */
@Component
public class SpiPluginRegistry implements PluginRegistryPort {

    private static final Logger log = LoggerFactory.getLogger(SpiPluginRegistry.class);

    private final ProjectMindProperties properties;
    private final List<ProjectMindPlugin> discovered = new ArrayList<>();
    private final List<ProjectMindPlugin> enabled = new ArrayList<>();
    private boolean initialized;

    public SpiPluginRegistry(ProjectMindProperties properties) {
        this.properties = properties;
        loadDiscoveredPlugins();
    }

    @PostConstruct
    void start() {
        initialize(buildContext());
    }

    @PreDestroy
    void stop() {
        shutdown();
    }

    @Override
    public synchronized void initialize(PluginContext context) {
        if (initialized) {
            return;
        }
        enabled.clear();
        for (ProjectMindPlugin plugin : discovered) {
            if (isEnabled(plugin.getName(), context)) {
                try {
                    plugin.onLoad(context);
                    enabled.add(plugin);
                    log.info("Enabled ProjectMind plugin: {}", plugin.getName());
                } catch (Exception ex) {
                    log.warn("Failed to initialize plugin {}: {}", plugin.getName(), ex.getMessage());
                }
            } else {
                log.debug("Plugin disabled by configuration: {}", plugin.getName());
            }
        }
        initialized = true;
        log.info("ProjectMind plugin registry ready: {}/{} enabled",
                enabled.size(), discovered.size());
    }

    @Override
    public synchronized void shutdown() {
        for (ProjectMindPlugin plugin : enabled) {
            try {
                plugin.onShutdown();
            } catch (Exception ex) {
                log.warn("Error shutting down plugin {}: {}", plugin.getName(), ex.getMessage());
            }
        }
        enabled.clear();
        initialized = false;
    }

    @Override
    public List<ProjectMindPlugin> getPlugins() {
        return List.copyOf(discovered);
    }

    @Override
    public List<ProjectMindPlugin> getEnabledPlugins() {
        return List.copyOf(enabled);
    }

    @Override
    public Optional<ProjectMindPlugin> findByName(String name) {
        return discovered.stream()
                .filter(plugin -> plugin.getName().equals(name))
                .findFirst();
    }

    private void loadDiscoveredPlugins() {
        Map<String, ProjectMindPlugin> byName = new LinkedHashMap<>();
        ServiceLoader.load(ProjectMindPlugin.class).stream()
                .map(ServiceLoader.Provider::get)
                .forEach(plugin -> {
                    ProjectMindPlugin existing = byName.putIfAbsent(plugin.getName(), plugin);
                    if (existing != null) {
                        log.warn("Duplicate plugin name {} ignored", plugin.getName());
                    }
                });
        discovered.addAll(byName.values());
        if (discovered.isEmpty()) {
            log.info("No ProjectMind plugins discovered on classpath");
        } else {
            log.info("Discovered {} ProjectMind plugin(s): {}",
                    discovered.size(),
                    discovered.stream().map(ProjectMindPlugin::getName).toList());
        }
    }

    private PluginContext buildContext() {
        ProjectMindProperties.Plugins pluginSettings = properties.getPlugins();
        return new PluginContext(
                Map.of(
                        "plugins.enabled", String.valueOf(pluginSettings.isEnabled()),
                        "plugins.auto-detect", String.valueOf(pluginSettings.isAutoDetect()),
                        "plugins.exclude", String.join(",", pluginSettings.getExclude())),
                pluginSettings.getInclude());
    }

    private static boolean isEnabled(String pluginName, PluginContext context) {
        String enabledFlag = context.properties().get("plugins.enabled");
        if ("false".equalsIgnoreCase(enabledFlag)) {
            return false;
        }
        List<String> include = context.enabledPluginNames();
        if (!include.isEmpty()) {
            return include.contains(pluginName);
        }
        String excludeRaw = context.properties().get("plugins.exclude");
        if (excludeRaw != null && !excludeRaw.isBlank()) {
            for (String excluded : excludeRaw.split(",")) {
                if (pluginName.equals(excluded.trim())) {
                    return false;
                }
            }
        }
        return true;
    }
}
