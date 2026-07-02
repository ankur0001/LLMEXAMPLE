package com.projectmind.adapter;

import com.projectmind.adapter.config.ProjectMindProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for all ProjectMind adapter modules.
 */
@Configuration
@ComponentScan(basePackages = "com.projectmind.adapter")
@EnableConfigurationProperties(ProjectMindProperties.class)
public class AdapterConfiguration {
}
