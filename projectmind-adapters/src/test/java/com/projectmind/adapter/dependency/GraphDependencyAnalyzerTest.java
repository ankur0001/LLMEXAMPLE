package com.projectmind.adapter.dependency;

import com.projectmind.adapter.parser.treesitter.TreeSitterLanguageParser;
import com.projectmind.adapter.parser.treesitter.TreeSitterRuntime;
import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.GraphEdgeType;
import com.projectmind.core.domain.GraphNodeType;
import com.projectmind.core.domain.KnowledgeGraph;
import com.projectmind.core.domain.ParsedFile;
import com.projectmind.core.domain.ParsedMethodCall;
import com.projectmind.core.domain.ParsedType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GraphDependencyAnalyzerTest {

    private static TreeSitterLanguageParser parser;
    private final GraphDependencyAnalyzer analyzer = new GraphDependencyAnalyzer();

    @BeforeAll
    static void setUpParser() {
        assumeTrue(TreeSitterRuntime.isNativeAvailable(), "Tree-sitter native library unavailable");
        TreeSitterRuntime.ensureLoaded();
        parser = new TreeSitterLanguageParser();
    }

    @Test
    void buildsImportCallAndInjectionEdgesFromJavaSample() throws Exception {
        String source = java.nio.file.Files.readString(
                Path.of("src/test/resources/samples/UserService.java"));
        ParsedFile parsed = parser.parse(
                Path.of("src/main/java/com/example/service/UserService.java"),
                FileType.JAVA,
                source);

        KnowledgeGraph graph = analyzer.analyze(List.of(parsed), null);

        assertThat(graph.nodes()).anyMatch(n ->
                n.id().equals("type:com.example.service.UserService")
                        && n.type() == GraphNodeType.CLASS);
        assertThat(graph.edges()).anyMatch(e ->
                e.type() == GraphEdgeType.IMPORTS
                        && e.sourceId().equals("type:com.example.service.UserService")
                        && e.label().contains("UserRepository"));
        assertThat(graph.edges()).anyMatch(e ->
                e.type() == GraphEdgeType.CALLS
                        && e.label().equals("findById"));
        assertThat(graph.edges()).anyMatch(e ->
                e.type() == GraphEdgeType.INJECTS
                        && e.sourceId().equals("type:com.example.service.UserService"));
    }

    @Test
    void extractsMavenDependencies() throws Exception {
        String pom = java.nio.file.Files.readString(Path.of("src/test/resources/samples/pom.xml"));
        ParsedFile parsed = parser.parse(Path.of("pom.xml"), FileType.MAVEN, pom);

        KnowledgeGraph graph = analyzer.analyze(List.of(parsed), null);

        assertThat(graph.edges()).anyMatch(e ->
                e.type() == GraphEdgeType.DEPENDS_ON
                        && e.label().contains("spring-boot-starter-web"));
    }

    @Test
    void mergesExistingGraphKeepingUnchangedFiles() {
        KnowledgeGraph existing = new KnowledgeGraph(
                List.of(
                        new com.projectmind.core.domain.GraphNode(
                                "type:com.example.legacy.LegacyService",
                                "LegacyService",
                                GraphNodeType.SERVICE,
                                "src/main/java/com/example/legacy/LegacyService.java",
                                "com.example.legacy")),
                List.of());

        ParsedFile updated = parsedLayerFile(
                "com.example.web",
                controllerType(),
                List.of(new ParsedMethodCall("UserController", "getUser", "UserService", "findAll", 10)));

        KnowledgeGraph graph = analyzer.analyze(List.of(updated), existing);

        assertThat(graph.nodes()).anyMatch(n -> n.name().equals("LegacyService"));
        assertThat(graph.nodes()).anyMatch(n -> n.name().equals("UserController"));
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
                List.of(new com.projectmind.core.domain.ParsedMethod(
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
}
