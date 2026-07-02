package com.projectmind.adapter.parser;

import com.projectmind.adapter.parser.treesitter.TreeSitterLanguageParser;
import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.GraphNodeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TreeSitterLanguageParserTest {

    private static TreeSitterLanguageParser parser;

    @BeforeAll
    static void setUp() {
        assumeTrue(com.projectmind.adapter.parser.treesitter.TreeSitterRuntime.isNativeAvailable(),
                "Tree-sitter native library requires macOS/Linux with compatible native binaries");
        com.projectmind.adapter.parser.treesitter.TreeSitterRuntime.ensureLoaded();
        parser = new TreeSitterLanguageParser();
    }

    @Test
    void parsesJavaClassWithSpringAnnotations() throws Exception {
        String source = java.nio.file.Files.readString(
                Path.of("src/test/resources/samples/UserService.java"));
        Path path = Path.of("src/main/java/com/example/service/UserService.java");

        var parsed = parser.parse(path, FileType.JAVA, source);

        assertThat(parsed.packageName()).isEqualTo("com.example.service");
        assertThat(parsed.imports()).extracting(i -> i.importedName())
                .contains("org.springframework.stereotype.Service", "com.example.repository.UserRepository");
        assertThat(parsed.types()).hasSize(1);

        var service = parsed.types().get(0);
        assertThat(service.name()).isEqualTo("UserService");
        assertThat(service.kind()).isEqualTo(GraphNodeType.CLASS);
        assertThat(service.annotations()).anyMatch(a -> a.contains("Service"));
        assertThat(service.methods()).extracting(m -> m.name())
                .contains("UserService", "findById");
        assertThat(parsed.methodCalls()).isNotEmpty();
        assertThat(parsed.methodCalls()).anyMatch(c -> "findById".equals(c.targetMethod()));
    }

    @Test
    void parsesMavenPomDependencies() throws Exception {
        String source = java.nio.file.Files.readString(
                Path.of("src/test/resources/samples/pom.xml"));

        var parsed = parser.parse(Path.of("pom.xml"), FileType.MAVEN, source);

        assertThat(parsed.imports()).isNotEmpty();
        assertThat(parsed.imports()).anyMatch(i -> i.importedName().contains("spring-boot-starter-web"));
    }

    @Test
    void parsesYamlConfiguration() {
        String source = """
                server:
                  port: 8080
                spring:
                  application:
                    name: demo
                """;

        var parsed = parser.parse(Path.of("application.yml"), FileType.YAML, source);

        assertThat(parsed.annotations()).contains("server", "spring");
    }

    @Test
    void parsesPropertiesFile() {
        String source = """
                server.port=8080
                spring.application.name=demo
                """;

        var parsed = parser.parse(Path.of("application.properties"), FileType.PROPERTIES, source);

        assertThat(parsed.annotations()).isNotEmpty();
    }

    @Test
    void parsesSqlCreateTable() {
        String source = """
                CREATE TABLE users (
                  id BIGINT PRIMARY KEY,
                  email VARCHAR(255)
                );
                """;

        var parsed = parser.parse(Path.of("schema.sql"), FileType.SQL, source);

        assertThat(parsed.types()).isNotEmpty();
        assertThat(parsed.types().get(0).kind()).isEqualTo(GraphNodeType.ENTITY);
    }

    @Test
    void supportsExpectedFileTypes() {
        assertThat(parser.supports(FileType.JAVA)).isTrue();
        assertThat(parser.supports(FileType.MAVEN)).isTrue();
        assertThat(parser.supports(FileType.YAML)).isTrue();
        assertThat(parser.supports(FileType.OTHER)).isFalse();
    }
}
