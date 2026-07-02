package com.projectmind.adapter.parser.treesitter;

import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.ParsedFile;
import com.projectmind.core.port.LanguageParserPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Tree-sitter backed language parser for Java and configuration file types.
 */
@Component
public class TreeSitterLanguageParser implements LanguageParserPort {

    private static final Logger log = LoggerFactory.getLogger(TreeSitterLanguageParser.class);

    @Override
    public ParsedFile parse(Path filePath, FileType fileType, String sourceCode) {
        if (!TreeSitterRuntime.isNativeAvailable()) {
            log.warn("Tree-sitter native library unavailable on this OS; returning empty parse for {}", filePath);
            return emptyParse(filePath, fileType);
        }

        try {
            return switch (fileType) {
                case JAVA -> JavaAstExtractor.extract(filePath, TreeSitterRuntime.parse(
                        TreeSitterRuntime.Grammar.JAVA, sourceCode));
                case KOTLIN -> ConfigAstExtractor.extractKotlinGradle(filePath, TreeSitterRuntime.parse(
                        TreeSitterRuntime.Grammar.KOTLIN, sourceCode));
                case MAVEN, XML -> ConfigAstExtractor.extractXmlFallback(filePath, fileType, sourceCode);
                case YAML, KUBERNETES -> ConfigAstExtractor.extractYaml(filePath, TreeSitterRuntime.parse(
                        TreeSitterRuntime.Grammar.YAML, sourceCode));
                case PROPERTIES -> ConfigAstExtractor.extractPropertiesFallback(filePath, sourceCode);
                case SQL -> ConfigAstExtractor.extractSqlFallback(filePath, sourceCode);
                case GRADLE -> parseGradle(filePath, sourceCode);
                case JSON -> {
                    TreeSitterRuntime.parse(TreeSitterRuntime.Grammar.JSON, sourceCode);
                    yield emptyParse(filePath, fileType);
                }
                default -> emptyParse(filePath, fileType);
            };
        } catch (TreeSitterParseException ex) {
            log.warn("Tree-sitter parse failed for {}: {}", filePath, ex.getMessage());
            return emptyParse(filePath, fileType);
        }
    }

    private ParsedFile parseGradle(Path filePath, String sourceCode) {
        String name = filePath.getFileName().toString().toLowerCase();
        if (name.endsWith(".kts")) {
            return ConfigAstExtractor.extractKotlinGradle(filePath, TreeSitterRuntime.parse(
                    TreeSitterRuntime.Grammar.KOTLIN, sourceCode));
        }
        return new ParsedFile(
                filePath.toString(),
                FileType.GRADLE,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of("groovy-build-script"));
    }

    @Override
    public boolean supports(FileType fileType) {
        return switch (fileType) {
            case JAVA, KOTLIN, MAVEN, XML, YAML, KUBERNETES, PROPERTIES, SQL, GRADLE, JSON -> true;
            default -> false;
        };
    }

    private ParsedFile emptyParse(Path filePath, FileType fileType) {
        return new ParsedFile(
                filePath.toString(),
                fileType,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
