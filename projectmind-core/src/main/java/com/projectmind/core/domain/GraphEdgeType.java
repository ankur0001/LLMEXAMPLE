package com.projectmind.core.domain;

/**
 * Types of relationships between knowledge graph nodes.
 */
public enum GraphEdgeType {
    IMPORTS,
    EXTENDS,
    IMPLEMENTS,
    CALLS,
    INJECTS,
    MAPS_TO,
    SECURES,
    CONFIGURES,
    DEPENDS_ON,
    CONTAINS
}
