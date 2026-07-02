package com.projectmind.plugin.spring;

import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.GraphEdge;
import com.projectmind.core.domain.GraphEdgeType;
import com.projectmind.core.domain.GraphNode;
import com.projectmind.core.domain.GraphNodeType;
import com.projectmind.core.domain.KnowledgeGraph;
import com.projectmind.core.domain.ParsedFile;
import com.projectmind.core.domain.ParsedMethod;
import com.projectmind.core.domain.ParsedMethodCall;
import com.projectmind.core.domain.ParsedType;
import com.projectmind.core.graph.GraphEnhancementContext;
import com.projectmind.core.graph.GraphNodeIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpringBootPluginTest {

    private SpringBootPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new SpringBootPlugin();
    }

    @Test
    void resolvesServiceStereotype() {
        ParsedFile parsed = parsedLayerFile(
                "com.example.service",
                serviceType(),
                List.of(new ParsedMethodCall("UserService", "findById", "userRepository", "findById", 12)));

        KnowledgeGraph graph = enhance(List.of(parsed));

        assertThat(graph.nodes()).anyMatch(n ->
                n.id().equals("type:com.example.service.UserService")
                        && n.type() == GraphNodeType.SERVICE);
    }

    @Test
    void detectsSpringLayerFlowFromControllerToServiceToRepository() {
        ParsedFile controller = parsedLayerFile(
                "com.example.web",
                controllerType(),
                List.of(new ParsedMethodCall("UserController", "getUser", "UserService", "findById", 10)));

        ParsedFile service = parsedLayerFile(
                "com.example.service",
                serviceType(),
                List.of(new ParsedMethodCall("UserService", "findById", "UserRepository", "findById", 12)));

        ParsedFile repository = parsedLayerFile("com.example.repository", repositoryType(), List.of());
        List<ParsedFile> files = List.of(controller, service, repository);

        KnowledgeGraph graph = enhance(files);

        assertThat(graph.edges()).anyMatch(e ->
                e.type() == GraphEdgeType.DEPENDS_ON
                        && e.sourceId().equals("type:com.example.web.UserController")
                        && e.targetId().equals("type:com.example.service.UserService")
                        && e.label().contains("controller->service"));
        assertThat(graph.edges()).anyMatch(e ->
                e.type() == GraphEdgeType.DEPENDS_ON
                        && e.sourceId().equals("type:com.example.service.UserService")
                        && e.targetId().equals("type:com.example.repository.UserRepository")
                        && e.label().contains("service->repository"));
    }

    @Test
    void mapsEntityToDatabaseTable() {
        ParsedType entity = new ParsedType(
                "User",
                GraphNodeType.CLASS,
                "",
                List.of(),
                List.of(),
                List.of("@Entity", "@Table(name=\"users\")"),
                1,
                20);

        ParsedFile parsed = new ParsedFile(
                "src/main/java/com/example/domain/User.java",
                FileType.JAVA,
                "com.example.domain",
                List.of(entity),
                List.of(),
                List.of(),
                List.of());

        KnowledgeGraph graph = enhance(List.of(parsed));

        assertThat(graph.nodes()).anyMatch(n -> n.id().equals("db:users") && n.type() == GraphNodeType.DATABASE);
        assertThat(graph.edges()).anyMatch(e ->
                e.type() == GraphEdgeType.MAPS_TO
                        && e.sourceId().equals("type:com.example.domain.User")
                        && e.targetId().equals("db:users"));
    }

    @Test
    void detectsSecurityFilterConfiguration() {
        ParsedType securityConfig = new ParsedType(
                "SecurityConfig",
                GraphNodeType.CLASS,
                "",
                List.of(),
                List.of(new ParsedMethod(
                        "securityFilterChain",
                        "SecurityFilterChain",
                        List.of("HttpSecurity http"),
                        List.of("@Bean"),
                        5,
                        15)),
                List.of("@Configuration", "@EnableWebSecurity"),
                1,
                20);

        ParsedFile parsed = new ParsedFile(
                "src/main/java/com/example/config/SecurityConfig.java",
                FileType.JAVA,
                "com.example.config",
                List.of(securityConfig),
                List.of(),
                List.of(),
                List.of());

        KnowledgeGraph graph = enhance(List.of(parsed));

        assertThat(graph.nodes()).anyMatch(n ->
                n.id().equals("type:com.example.config.SecurityConfig")
                        && n.type() == GraphNodeType.FILTER);
        assertThat(graph.edges()).anyMatch(e ->
                e.type() == GraphEdgeType.SECURES
                        && e.sourceId().equals("type:com.example.config.SecurityConfig"));
    }

    @Test
    void detectsFeignClientDependency() {
        ParsedType feignClient = new ParsedType(
                "BillingClient",
                GraphNodeType.INTERFACE,
                "",
                List.of(),
                List.of(),
                List.of("@FeignClient(name = \"billing-service\")"),
                1,
                10);
        ParsedFile parsed = parsedLayerFile("com.example.client", feignClient, List.of());

        KnowledgeGraph graph = enhance(List.of(parsed));

        assertThat(graph.edges()).anyMatch(e ->
                e.type() == GraphEdgeType.DEPENDS_ON
                        && e.sourceId().equals("type:com.example.client.BillingClient")
                        && e.label().equals("billing-service"));
    }

    private KnowledgeGraph enhance(List<ParsedFile> files) {
        GraphEnhancementContext context = new GraphEnhancementContext(new KnowledgeGraph(List.of(), List.of()));
        seedBaseGraph(context, files);
        for (ParsedFile file : files) {
            plugin.enhance(context, file);
        }
        plugin.finalizeGraph(context);
        return context.toGraph();
    }

    private static void seedBaseGraph(GraphEnhancementContext context, List<ParsedFile> files) {
        java.util.Map<String, String> typeIndex = new java.util.LinkedHashMap<>();
        for (ParsedFile file : files) {
            String pkg = file.packageName() != null ? file.packageName() : "";
            for (ParsedType type : file.types()) {
                String typeId = GraphNodeIds.typeId(pkg, type.name());
                typeIndex.put(type.name(), typeId);
                context.addOrUpdateNode(new GraphNode(
                        typeId, type.name(), type.kind(), file.relativePath(), pkg));
            }
        }
        for (ParsedFile file : files) {
            String pkg = file.packageName() != null ? file.packageName() : "";
            for (ParsedMethodCall call : file.methodCalls()) {
                if (call.callerType().isBlank() || call.targetType().isBlank()) {
                    continue;
                }
                String callerId = GraphNodeIds.typeId(pkg, call.callerType());
                String targetId = typeIndex.getOrDefault(
                        call.targetType(),
                        GraphNodeIds.typeId(pkg, call.targetType()));
                context.addEdge(new GraphEdge(callerId, targetId, GraphEdgeType.INJECTS, call.targetType()));
            }
        }
    }

    private static ParsedFile parsedLayerFile(
            String pkg,
            ParsedType type,
            List<ParsedMethodCall> calls) {
        return new ParsedFile(
                "src/main/java/" + pkg.replace('.', '/') + "/" + type.name() + ".java",
                FileType.JAVA,
                pkg,
                List.of(type),
                List.of(),
                calls,
                List.of());
    }

    private static ParsedType controllerType() {
        return new ParsedType(
                "UserController",
                GraphNodeType.CLASS,
                "",
                List.of(),
                List.of(new ParsedMethod(
                        "UserController",
                        "void",
                        List.of("UserService userService"),
                        List.of(),
                        3,
                        5)),
                List.of("@RestController"),
                1,
                20);
    }

    private static ParsedType serviceType() {
        return new ParsedType(
                "UserService",
                GraphNodeType.CLASS,
                "",
                List.of(),
                List.of(new ParsedMethod(
                        "UserService",
                        "void",
                        List.of("UserRepository userRepository"),
                        List.of(),
                        3,
                        5)),
                List.of("@Service"),
                1,
                20);
    }

    private static ParsedType repositoryType() {
        return new ParsedType(
                "UserRepository",
                GraphNodeType.INTERFACE,
                "",
                List.of(),
                List.of(),
                List.of("@Repository"),
                1,
                10);
    }
}
