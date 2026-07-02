package com.projectmind.core.domain;

/**
 * Supported source and configuration file categories detected during repository scan.
 */
public enum FileType {
    JAVA,
    KOTLIN,
    GRADLE,
    MAVEN,
    YAML,
    PROPERTIES,
    SQL,
    XML,
    JSON,
    DOCKER,
    KUBERNETES,
    MARKDOWN,
    SHELL,
    OTHER
}
