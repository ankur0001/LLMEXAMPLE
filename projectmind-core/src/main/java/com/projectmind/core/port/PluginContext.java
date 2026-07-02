package com.projectmind.core.port;

import java.util.List;
import java.util.Map;

/**
 * Runtime context passed to plugins during initialization.
 */
public record PluginContext(
        Map<String, String> properties,
        List<String> enabledPluginNames
) {
    public PluginContext {
        properties = properties != null ? Map.copyOf(properties) : Map.of();
        enabledPluginNames = enabledPluginNames != null ? List.copyOf(enabledPluginNames) : List.of();
    }

    public static PluginContext empty() {
        return new PluginContext(Map.of(), List.of());
    }
}
