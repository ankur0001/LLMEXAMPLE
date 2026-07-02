package com.projectmind.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Registered ProjectMind plugin")
public record PluginInfo(
        @Schema(example = "spring-boot") String name,
        @Schema(description = "Whether the plugin is enabled for this runtime") boolean enabled
) {
}
