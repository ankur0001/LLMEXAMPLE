package com.projectmind.plugin.spring;

import com.projectmind.core.domain.GraphNodeType;
import com.projectmind.core.domain.ParsedMethod;
import com.projectmind.core.domain.ParsedType;

import java.util.List;

/**
 * Detects Spring stereotypes and security patterns from parsed annotations.
 */
final class SpringArchitectureDetector {

    private SpringArchitectureDetector() {
    }

    static GraphNodeType resolveNodeType(ParsedType type) {
        if (hasAnyAnnotation(type.annotations(), "Entity", "Table", "jakarta.persistence.Entity")) {
            return GraphNodeType.ENTITY;
        }
        if (hasAnnotation(type.annotations(), "EnableWebSecurity")
                || type.name().contains("Security")
                || declaresSecurityFilterChain(type)) {
            return GraphNodeType.FILTER;
        }
        if (hasAnyAnnotation(type.annotations(), "RestController", "Controller")) {
            return GraphNodeType.CONTROLLER;
        }
        if (hasAnnotation(type.annotations(), "Service")) {
            return GraphNodeType.SERVICE;
        }
        if (hasAnnotation(type.annotations(), "Repository")) {
            return GraphNodeType.REPOSITORY;
        }
        if (hasAnnotation(type.annotations(), "Configuration")) {
            return GraphNodeType.CONFIGURATION;
        }
        return type.kind();
    }

    static boolean isLayerFlow(GraphNodeType source, GraphNodeType target) {
        return (source == GraphNodeType.CONTROLLER && target == GraphNodeType.SERVICE)
                || (source == GraphNodeType.SERVICE && target == GraphNodeType.REPOSITORY)
                || (source == GraphNodeType.CONTROLLER && target == GraphNodeType.REPOSITORY);
    }

    static String resolveTableName(ParsedType type) {
        for (String annotation : type.annotations()) {
            String table = extractAnnotationValue(annotation, "Table");
            if (!table.isEmpty()) {
                return table;
            }
        }
        return camelToSnake(type.name());
    }

    static String extractBeanType(ParsedMethod method) {
        String returnType = method.returnType();
        if (returnType == null || returnType.isBlank() || "void".equals(returnType)) {
            return "";
        }
        return returnType;
    }

    static boolean isFeignClient(ParsedType type) {
        return hasAnyAnnotation(type.annotations(), "FeignClient");
    }

    static String extractFeignClientName(ParsedType type) {
        for (String annotation : type.annotations()) {
            if (!annotation.contains("FeignClient")) {
                continue;
            }
            String name = extractAnnotationValue(annotation, "name");
            if (!name.isEmpty()) {
                return name;
            }
            String value = extractAnnotationValue(annotation, "value");
            if (!value.isEmpty()) {
                return value;
            }
        }
        return type.name() + "Client";
    }

    private static boolean declaresSecurityFilterChain(ParsedType type) {
        for (ParsedMethod method : type.methods()) {
            if ("SecurityFilterChain".equals(method.returnType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnnotation(List<String> annotations, String simpleName) {
        return annotations.stream().anyMatch(a -> a.contains("@" + simpleName) || a.contains(simpleName));
    }

    private static boolean hasAnyAnnotation(List<String> annotations, String... simpleNames) {
        for (String name : simpleNames) {
            if (hasAnnotation(annotations, name)) {
                return true;
            }
        }
        return false;
    }

    private static String extractAnnotationValue(String annotation, String name) {
        if (!annotation.contains(name)) {
            return "";
        }
        int nameIdx = annotation.indexOf("name");
        if (nameIdx < 0) {
            return "";
        }
        int eq = annotation.indexOf('=', nameIdx);
        if (eq < 0) {
            return "";
        }
        int start = annotation.indexOf('"', eq);
        int end = annotation.indexOf('"', start + 1);
        if (start >= 0 && end > start) {
            return annotation.substring(start + 1, end);
        }
        return "";
    }

    private static String camelToSnake(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
