package com.projectmind.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Plugin registry response")
public record PluginsResponse(
        List<PluginInfo> plugins
) {
}
