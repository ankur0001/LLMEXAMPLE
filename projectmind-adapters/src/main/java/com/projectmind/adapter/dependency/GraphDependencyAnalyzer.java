package com.projectmind.adapter.dependency;

import com.projectmind.core.domain.GraphEdge;
import com.projectmind.core.domain.GraphEdgeType;
import com.projectmind.core.domain.GraphNode;
import com.projectmind.core.domain.GraphNodeType;
import com.projectmind.core.domain.KnowledgeGraph;
import com.projectmind.core.domain.ParsedFile;
import com.projectmind.core.domain.ParsedImport;
import com.projectmind.core.domain.ParsedMethod;
import com.projectmind.core.domain.ParsedMethodCall;
import com.projectmind.core.domain.ParsedType;
import com.projectmind.core.graph.GraphNodeIds;
import com.projectmind.core.port.DependencyAnalyzerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a dependency and architecture graph from parsed repository files.
 */
@Component
public class GraphDependencyAnalyzer implements DependencyAnalyzerPort {

    private static final Logger log = LoggerFactory.getLogger(GraphDependencyAnalyzer.class);

    @Override
    public KnowledgeGraph analyze(List<ParsedFile> parsedFiles, KnowledgeGraph existing) {
        log.debug("Analyzing dependencies for {} parsed files", parsedFiles.size());

        java.util.Set<String> rebuiltPaths = parsedFiles.stream()
                .map(ParsedFile::relativePath)
                .collect(java.util.stream.Collectors.toSet());

        Map<String, GraphNode> nodes = new LinkedHashMap<>();
        List<GraphEdge> edges = new ArrayList<>();
        mergeExistingGraph(existing, rebuiltPaths, nodes, edges);

        Map<String, String> typeIndex = buildTypeIndex(parsedFiles);

        for (ParsedFile file : parsedFiles) {
            switch (file.fileType()) {
                case JAVA, KOTLIN -> analyzeSourceFile(file, nodes, edges, typeIndex);
                case MAVEN -> analyzeMavenFile(file, nodes, edges);
                case YAML, KUBERNETES, PROPERTIES -> analyzeConfigFile(file, nodes, edges);
                case SQL -> analyzeSqlFile(file, nodes, edges);
                default -> { }
            }
        }

        return new KnowledgeGraph(List.copyOf(nodes.values()), List.copyOf(edges));
    }

    private static void mergeExistingGraph(
            KnowledgeGraph existing,
            java.util.Set<String> rebuiltPaths,
            Map<String, GraphNode> nodes,
            List<GraphEdge> edges) {
        if (existing == null) {
            return;
        }
        for (GraphNode node : existing.nodes()) {
            if (node.sourceFile() == null || !rebuiltPaths.contains(node.sourceFile())) {
                addNode(nodes, node);
            }
        }
        java.util.Set<String> keptNodeIds = nodes.keySet();
        for (GraphEdge edge : existing.edges()) {
            if (keptNodeIds.contains(edge.sourceId()) && keptNodeIds.contains(edge.targetId())) {
                addEdge(edges, edge);
            }
        }
    }

    private Map<String, String> buildTypeIndex(List<ParsedFile> parsedFiles) {
        Map<String, String> index = new LinkedHashMap<>();
        for (ParsedFile file : parsedFiles) {
            String pkg = file.packageName() != null ? file.packageName() : "";
            for (ParsedType type : file.types()) {
                String typeId = GraphNodeIds.typeId(pkg, type.name());
                index.putIfAbsent(type.name(), typeId);
                index.putIfAbsent(qualifyType(pkg, type.name()), typeId);
            }
        }
        return index;
    }

    private void analyzeSourceFile(
            ParsedFile file,
            Map<String, GraphNode> nodes,
            List<GraphEdge> edges,
            Map<String, String> typeIndex) {
        String pkg = file.packageName() != null ? file.packageName() : "";

        if (!pkg.isBlank()) {
            addNode(nodes, new GraphNode(
                    GraphNodeIds.packageId(pkg),
                    pkg,
                    GraphNodeType.PACKAGE,
                    file.relativePath(),
                    pkg));
        }

        for (ParsedType type : file.types()) {
            GraphNodeType nodeType = type.kind();
            String typeId = GraphNodeIds.typeId(pkg, type.name());

            addNode(nodes, new GraphNode(
                    typeId,
                    type.name(),
                    nodeType,
                    file.relativePath(),
                    pkg));

            if (!pkg.isBlank()) {
                addEdge(edges, new GraphEdge(
                        GraphNodeIds.packageId(pkg),
                        typeId,
                        GraphEdgeType.CONTAINS,
                        type.name()));
            }

            for (ParsedImport imp : file.imports()) {
                String targetId = GraphNodeIds.importTargetId(imp.importedName());
                addNode(nodes, new GraphNode(
                        targetId,
                        simpleName(imp.importedName()),
                        GraphNodeType.MODULE,
                        file.relativePath(),
                        packageOf(imp.importedName())));
                addEdge(edges, new GraphEdge(typeId, targetId, GraphEdgeType.IMPORTS, imp.importedName()));
            }

            if (type.superClass() != null && !type.superClass().isBlank()) {
                String superId = resolveTypeReference(type.superClass(), pkg, file.relativePath(), nodes, typeIndex);
                addEdge(edges, new GraphEdge(typeId, superId, GraphEdgeType.EXTENDS, type.superClass()));
            }

            for (String iface : type.interfaces()) {
                String ifaceId = resolveTypeReference(iface, pkg, file.relativePath(), nodes, typeIndex);
                addEdge(edges, new GraphEdge(typeId, ifaceId, GraphEdgeType.IMPLEMENTS, iface));
            }

            analyzeTypeRelationships(type, typeId, pkg, file, nodes, edges, typeIndex);
        }

        for (ParsedMethodCall call : file.methodCalls()) {
            if (call.callerType().isBlank() || call.targetMethod().isBlank()) {
                continue;
            }
            String callerId = GraphNodeIds.typeId(pkg, call.callerType());
            String targetId = call.targetType().isBlank()
                    ? GraphNodeIds.importTargetId(call.targetMethod())
                    : resolveTypeReference(call.targetType(), pkg, file.relativePath(), nodes, typeIndex);
            addEdge(edges, new GraphEdge(
                    callerId,
                    targetId,
                    GraphEdgeType.CALLS,
                    call.targetMethod()));
        }
    }

    private void analyzeTypeRelationships(
            ParsedType type,
            String typeId,
            String pkg,
            ParsedFile file,
            Map<String, GraphNode> nodes,
            List<GraphEdge> edges,
            Map<String, String> typeIndex) {
        for (ParsedMethod method : type.methods()) {
            detectInjections(type.name(), typeId, method, pkg, file.relativePath(), nodes, edges, typeIndex);
        }
    }

    private void detectInjections(
            String typeName,
            String typeId,
            ParsedMethod method,
            String pkg,
            String sourceFile,
            Map<String, GraphNode> nodes,
            List<GraphEdge> edges,
            Map<String, String> typeIndex) {
        boolean isConstructor = method.name().equals(typeName);
        boolean injectable = method.annotations().stream().anyMatch(a -> a.contains("@Autowired")) || isConstructor;

        if (!injectable && method.parameters().isEmpty()) {
            return;
        }

        for (String parameter : method.parameters()) {
            String depType = extractParameterType(parameter);
            if (depType.isBlank()) {
                continue;
            }
            String depId = resolveTypeReference(depType, pkg, sourceFile, nodes, typeIndex);
            addEdge(edges, new GraphEdge(typeId, depId, GraphEdgeType.INJECTS, depType));
        }
    }

    private String resolveTypeReference(
            String typeName,
            String currentPackage,
            String sourceFile,
            Map<String, GraphNode> nodes,
            Map<String, String> typeIndex) {
        String simple = simpleName(typeName);
        if (typeIndex.containsKey(typeName)) {
            return typeIndex.get(typeName);
        }
        if (typeIndex.containsKey(simple)) {
            return typeIndex.get(simple);
        }
        String qualified = typeName.contains(".") ? typeName : qualifyType(currentPackage, simple);
        String typeId = GraphNodeIds.typeId(packageOf(qualified), simple);
        if (!nodes.containsKey(typeId)) {
            addNode(nodes, new GraphNode(
                    typeId,
                    simple,
                    GraphNodeType.CLASS,
                    sourceFile,
                    packageOf(qualified)));
        }
        return typeId;
    }

    private void analyzeMavenFile(ParsedFile file, Map<String, GraphNode> nodes, List<GraphEdge> edges) {
        String moduleId = GraphNodeIds.mavenModuleId(file.relativePath());
        addNode(nodes, new GraphNode(
                moduleId,
                simpleName(file.relativePath()),
                GraphNodeType.MODULE,
                file.relativePath(),
                ""));

        for (ParsedImport dep : file.imports()) {
            String depId = GraphNodeIds.importTargetId(dep.importedName());
            addNode(nodes, new GraphNode(
                    depId,
                    dep.importedName(),
                    GraphNodeType.MODULE,
                    file.relativePath(),
                    ""));
            addEdge(edges, new GraphEdge(moduleId, depId, GraphEdgeType.DEPENDS_ON, dep.importedName()));
        }
    }

    private void analyzeConfigFile(ParsedFile file, Map<String, GraphNode> nodes, List<GraphEdge> edges) {
        String configId = GraphNodeIds.configFileId(file.relativePath());
        addNode(nodes, new GraphNode(
                configId,
                simpleName(file.relativePath()),
                GraphNodeType.CONFIGURATION,
                file.relativePath(),
                ""));

        for (String key : file.annotations()) {
            if (key.isBlank()) {
                continue;
            }
            String keyId = GraphNodeIds.configKeyId(file.relativePath(), key);
            addNode(nodes, new GraphNode(
                    keyId,
                    key,
                    GraphNodeType.CONFIGURATION,
                    file.relativePath(),
                    ""));
            addEdge(edges, new GraphEdge(configId, keyId, GraphEdgeType.CONFIGURES, key));
        }
    }

    private void analyzeSqlFile(ParsedFile file, Map<String, GraphNode> nodes, List<GraphEdge> edges) {
        String fileId = GraphNodeIds.configFileId(file.relativePath());
        addNode(nodes, new GraphNode(
                fileId,
                simpleName(file.relativePath()),
                GraphNodeType.DATABASE,
                file.relativePath(),
                ""));

        for (ParsedType table : file.types()) {
            String dbId = GraphNodeIds.databaseId(table.name());
            addNode(nodes, new GraphNode(
                    dbId,
                    table.name(),
                    GraphNodeType.DATABASE,
                    file.relativePath(),
                    ""));
            addEdge(edges, new GraphEdge(
                    fileId,
                    dbId,
                    GraphEdgeType.MAPS_TO,
                    table.name()));
        }
    }

    private static String extractParameterType(String parameter) {
        String trimmed = parameter.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        int space = trimmed.lastIndexOf(' ');
        if (space > 0) {
            return trimmed.substring(0, space).trim();
        }
        int at = trimmed.indexOf('@');
        if (at >= 0) {
            trimmed = trimmed.substring(at + 1).trim();
            space = trimmed.indexOf(' ');
            return space > 0 ? trimmed.substring(0, space) : trimmed;
        }
        return trimmed;
    }

    private static String qualifyType(String pkg, String simpleName) {
        return pkg.isBlank() ? simpleName : pkg + "." + simpleName;
    }

    private static String packageOf(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot > 0 ? qualifiedName.substring(0, lastDot) : "";
    }

    private static String simpleName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('.'));
        if (slash >= 0 && path.contains(".")) {
            int dot = path.lastIndexOf('.');
            int segment = Math.max(path.lastIndexOf('/'), path.lastIndexOf(':'));
            return path.substring(Math.max(segment + 1, 0), dot > segment ? dot : path.length());
        }
        int dot = path.lastIndexOf('.');
        return dot > 0 ? path.substring(dot + 1) : path;
    }

    private static void addNode(Map<String, GraphNode> nodes, GraphNode node) {
        nodes.putIfAbsent(node.id(), node);
    }

    private static void addEdge(List<GraphEdge> edges, GraphEdge edge) {
        if (edges.stream().noneMatch(existing -> edgesEqual(existing, edge))) {
            edges.add(edge);
        }
    }

    private static boolean edgesEqual(GraphEdge a, GraphEdge b) {
        return a.sourceId().equals(b.sourceId())
                && a.targetId().equals(b.targetId())
                && a.type() == b.type();
    }
}
