package com.projectmind.plugin.spring;

import com.projectmind.core.domain.DocSection;
import com.projectmind.core.domain.DocSectionType;
import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.GraphEdge;
import com.projectmind.core.domain.GraphEdgeType;
import com.projectmind.core.domain.GraphNode;
import com.projectmind.core.domain.GraphNodeType;
import com.projectmind.core.domain.KnowledgeGraph;
import com.projectmind.core.domain.ParsedFile;
import com.projectmind.core.domain.ParsedMethod;
import com.projectmind.core.domain.ParsedType;
import com.projectmind.core.domain.ProjectMetadata;
import com.projectmind.core.domain.RepositoryIndex;
import com.projectmind.core.graph.GraphEnhancementContext;
import com.projectmind.core.graph.GraphNodeIds;
import com.projectmind.core.port.ProjectMindPlugin;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Enriches the knowledge graph with Spring Boot stereotypes, security, Feign, and layer flows.
 */
public class SpringBootPlugin implements ProjectMindPlugin {

    private static final Set<FileType> SUPPORTED = EnumSet.of(
            FileType.JAVA,
            FileType.KOTLIN,
            FileType.YAML,
            FileType.PROPERTIES,
            FileType.KUBERNETES);

    @Override
    public String getName() {
        return "spring-boot";
    }

    @Override
    public Set<FileType> supportedFileTypes() {
        return SUPPORTED;
    }

    @Override
    public void enhance(GraphEnhancementContext context, ParsedFile file) {
        switch (file.fileType()) {
            case JAVA, KOTLIN -> enhanceSourceFile(context, file);
            case YAML, PROPERTIES, KUBERNETES -> enhanceConfigFile(context, file);
            default -> { }
        }
    }

    @Override
    public void finalizeGraph(GraphEnhancementContext context) {
        Set<GraphEdgeType> relationshipTypes = Set.of(GraphEdgeType.INJECTS, GraphEdgeType.CALLS);
        for (GraphEdge edge : context.edges()) {
            if (!relationshipTypes.contains(edge.type())) {
                continue;
            }
            GraphNode source = context.findNode(edge.sourceId()).orElse(null);
            GraphNode target = context.findNode(edge.targetId()).orElse(null);
            if (source == null || target == null) {
                continue;
            }
            if (SpringArchitectureDetector.isLayerFlow(source.type(), target.type())) {
                context.addEdge(new GraphEdge(
                        edge.sourceId(),
                        edge.targetId(),
                        GraphEdgeType.DEPENDS_ON,
                        source.type().name().toLowerCase(Locale.ROOT)
                                + "->"
                                + target.type().name().toLowerCase(Locale.ROOT)));
            }
        }
    }

    @Override
    public List<DocSectionType> additionalSections() {
        return List.of(DocSectionType.STARTUP_FLOW);
    }

    @Override
    public List<DocSection> generateDocSections(
            Path repositoryPath,
            ProjectMetadata metadata,
            RepositoryIndex index,
            KnowledgeGraph graph) {
        long controllers = graph.nodes().stream().filter(n -> n.type() == GraphNodeType.CONTROLLER).count();
        long services = graph.nodes().stream().filter(n -> n.type() == GraphNodeType.SERVICE).count();
        long repositories = graph.nodes().stream().filter(n -> n.type() == GraphNodeType.REPOSITORY).count();
        if (controllers + services + repositories == 0) {
            return List.of();
        }

        String markdown = """
                ## Spring Boot Layer Summary

                This repository uses Spring Boot stereotypes detected from annotations:

                - **Controllers**: %d
                - **Services**: %d
                - **Repositories**: %d

                Layered request flow follows Controller → Service → Repository where injection edges exist.
                """.formatted(controllers, services, repositories);

        return List.of(new DocSection(
                DocSectionType.STARTUP_FLOW,
                "Spring Boot Layers",
                markdown,
                null));
    }

    private void enhanceSourceFile(GraphEnhancementContext context, ParsedFile file) {
        String pkg = file.packageName() != null ? file.packageName() : "";
        for (ParsedType type : file.types()) {
            String typeId = GraphNodeIds.typeId(pkg, type.name());
            GraphNodeType springType = SpringArchitectureDetector.resolveNodeType(type);
            context.updateNodeType(typeId, springType);

            if (springType == GraphNodeType.ENTITY) {
                String table = SpringArchitectureDetector.resolveTableName(type);
                String dbId = GraphNodeIds.databaseId(table);
                context.addOrUpdateNode(new GraphNode(
                        dbId, table, GraphNodeType.DATABASE, file.relativePath(), pkg));
                context.addEdge(new GraphEdge(typeId, dbId, GraphEdgeType.MAPS_TO, table));
            }

            if (springType == GraphNodeType.FILTER) {
                context.addOrUpdateNode(new GraphNode(
                        GraphNodeIds.databaseId("http"),
                        "http",
                        GraphNodeType.FILTER,
                        file.relativePath(),
                        pkg));
                context.addEdge(new GraphEdge(
                        typeId, GraphNodeIds.databaseId("http"), GraphEdgeType.SECURES, "http"));
            }

            if (springType == GraphNodeType.CONFIGURATION) {
                for (ParsedMethod method : type.methods()) {
                    if (method.annotations().stream().anyMatch(a -> a.contains("@Bean"))) {
                        String beanType = SpringArchitectureDetector.extractBeanType(method);
                        if (!beanType.isBlank()) {
                            String beanId = GraphNodeIds.typeId(pkg, simpleName(beanType));
                            context.addEdge(new GraphEdge(
                                    typeId, beanId, GraphEdgeType.CONFIGURES, method.name()));
                        }
                    }
                    if ("SecurityFilterChain".equals(method.returnType())) {
                        context.addEdge(new GraphEdge(
                                typeId,
                                GraphNodeIds.databaseId("http"),
                                GraphEdgeType.SECURES,
                                method.name()));
                    }
                }
            }

            if (SpringArchitectureDetector.isFeignClient(type)) {
                String serviceName = SpringArchitectureDetector.extractFeignClientName(type);
                String targetId = GraphNodeIds.importTargetId(serviceName);
                context.addOrUpdateNode(new GraphNode(
                        targetId,
                        serviceName,
                        GraphNodeType.SERVICE,
                        file.relativePath(),
                        pkg));
                context.addEdge(new GraphEdge(typeId, targetId, GraphEdgeType.DEPENDS_ON, serviceName));
            }
        }
    }

    private void enhanceConfigFile(GraphEnhancementContext context, ParsedFile file) {
        for (String key : file.annotations()) {
            if (key.isBlank()) {
                continue;
            }
            if (key.startsWith("spring.") || key.startsWith("server.")) {
                String beanHint = key.split("\\.")[0];
                String beanId = GraphNodeIds.importTargetId("spring." + beanHint);
                String keyId = GraphNodeIds.configKeyId(file.relativePath(), key);
                context.addOrUpdateNode(new GraphNode(
                        beanId, beanHint, GraphNodeType.CONFIGURATION, file.relativePath(), ""));
                context.addEdge(new GraphEdge(keyId, beanId, GraphEdgeType.CONFIGURES, key));
            }
        }
    }

    private static String simpleName(String typeName) {
        int dot = typeName.lastIndexOf('.');
        return dot > 0 ? typeName.substring(dot + 1) : typeName;
    }
}
