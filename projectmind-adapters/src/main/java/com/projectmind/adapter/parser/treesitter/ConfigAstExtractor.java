package com.projectmind.adapter.parser.treesitter;

import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.GraphNodeType;
import com.projectmind.core.domain.ParsedFile;
import com.projectmind.core.domain.ParsedImport;
import com.projectmind.core.domain.ParsedType;
import org.treesitter.TSNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts structural information from configuration and build files.
 */
public final class ConfigAstExtractor {

    private static final Pattern DEPENDENCY_BLOCK = Pattern.compile(
            "<dependency>\\s*.*?<groupId>\\s*(.*?)\\s*</groupId>\\s*.*?<artifactId>\\s*(.*?)\\s*</artifactId>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private ConfigAstExtractor() {
    }

    static ParsedFile extractXmlFallback(Path filePath, FileType fileType, String source) {
        List<ParsedImport> dependencies = new ArrayList<>();
        Matcher matcher = DEPENDENCY_BLOCK.matcher(source);
        while (matcher.find()) {
            String coordinates = matcher.group(1).trim() + ":" + matcher.group(2).trim();
            dependencies.add(new ParsedImport(coordinates, false, 0));
        }

        String root = "";
        Matcher project = Pattern.compile("<project\\b", Pattern.CASE_INSENSITIVE).matcher(source);
        if (project.find()) {
            root = "project";
        }

        List<String> keys = new ArrayList<>();
        Matcher tags = Pattern.compile("<([a-zA-Z0-9_.-]+)").matcher(source);
        while (tags.find()) {
            keys.add(tags.group(1));
        }

        return new ParsedFile(
                filePath.toString(),
                fileType,
                root,
                List.of(),
                dependencies,
                List.of(),
                keys.stream().distinct().limit(50).toList());
    }

    static ParsedFile extractYaml(Path filePath, TreeSitterRuntime.ParseResult parseResult) {
        String source = parseResult.source();
        List<String> keys = new ArrayList<>();
        TreeSitterNodes.walk(parseResult.root(), source, node -> {
            if ("block_mapping_pair".equals(node.getType()) || "flow_pair".equals(node.getType())) {
                String key = extractYamlKey(node, source);
                if (!key.isEmpty()) {
                    keys.add(key);
                }
            }
        });
        return new ParsedFile(
                filePath.toString(),
                FileType.YAML,
                "",
                List.of(),
                List.of(),
                List.of(),
                keys.stream().distinct().limit(50).toList());
    }

    static ParsedFile extractPropertiesFallback(Path filePath, String source) {
        List<String> keys = new ArrayList<>();
        for (String line : source.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq > 0) {
                keys.add(line.substring(0, eq).trim());
            }
        }
        return new ParsedFile(
                filePath.toString(),
                FileType.PROPERTIES,
                "",
                List.of(),
                List.of(),
                List.of(),
                keys.stream().limit(100).toList());
    }

    static ParsedFile extractSqlFallback(Path filePath, String source) {
        List<ParsedType> tables = new ArrayList<>();
        String upper = source.toUpperCase();
        int idx = 0;
        while ((idx = upper.indexOf("CREATE TABLE", idx)) >= 0) {
            int nameStart = idx + 12;
            while (nameStart < source.length() && Character.isWhitespace(source.charAt(nameStart))) {
                nameStart++;
            }
            int nameEnd = nameStart;
            while (nameEnd < source.length()) {
                char c = source.charAt(nameEnd);
                if (Character.isLetterOrDigit(c) || c == '_') {
                    nameEnd++;
                } else {
                    break;
                }
            }
            if (nameEnd > nameStart) {
                tables.add(new ParsedType(
                        source.substring(nameStart, nameEnd),
                        GraphNodeType.ENTITY,
                        "",
                        List.of(),
                        List.of(),
                        List.of(),
                        0,
                        0));
            }
            idx = nameEnd;
        }
        return new ParsedFile(
                filePath.toString(),
                FileType.SQL,
                "",
                tables,
                List.of(),
                List.of(),
                List.of());
    }

    static ParsedFile extractKotlinGradle(Path filePath, TreeSitterRuntime.ParseResult parseResult) {
        String source = parseResult.source();
        List<String> annotations = new ArrayList<>();
        TreeSitterNodes.walk(parseResult.root(), source, node -> {
            if ("call_expression".equals(node.getType())) {
                String call = TreeSitterNodes.text(node, source);
                if (call.contains("implementation") || call.contains("api") || call.contains("plugins")) {
                    annotations.add(call.length() > 120 ? call.substring(0, 120) + "..." : call);
                }
            }
        });
        return new ParsedFile(
                filePath.toString(),
                FileType.GRADLE,
                "",
                List.of(),
                List.of(),
                List.of(),
                annotations.stream().limit(30).toList());
    }

    private static String extractYamlKey(TSNode pairNode, String source) {
        for (int i = 0; i < pairNode.getChildCount(); i++) {
            TSNode child = pairNode.getChild(i);
            String type = child.getType();
            if ("flow_node".equals(type) || "plain_scalar".equals(type)
                    || "string_scalar".equals(type) || "block_mapping_key".equals(type)) {
                String text = TreeSitterNodes.text(child, source);
                if (!text.isEmpty() && !text.equals(":")) {
                    return text.replace(":", "").trim();
                }
            }
        }
        return TreeSitterNodes.text(pairNode, source).split(":")[0].trim();
    }
}
