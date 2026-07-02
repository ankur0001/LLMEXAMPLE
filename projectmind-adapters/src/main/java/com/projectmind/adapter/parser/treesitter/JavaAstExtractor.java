package com.projectmind.adapter.parser.treesitter;

import com.projectmind.core.domain.FileType;
import com.projectmind.core.domain.GraphNodeType;
import com.projectmind.core.domain.ParsedFile;
import com.projectmind.core.domain.ParsedImport;
import com.projectmind.core.domain.ParsedMethod;
import com.projectmind.core.domain.ParsedMethodCall;
import com.projectmind.core.domain.ParsedType;
import org.treesitter.TSNode;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Extracts {@link ParsedFile} structures from a Tree-sitter Java syntax tree.
 */
public final class JavaAstExtractor {

    private JavaAstExtractor() {
    }

    static ParsedFile extract(Path filePath, TreeSitterRuntime.ParseResult parseResult) {
        Context ctx = new Context(parseResult.source());
        walkWithContext(parseResult.root(), ctx);
        return new ParsedFile(
                filePath.toString(),
                FileType.JAVA,
                ctx.packageName,
                List.copyOf(ctx.types),
                List.copyOf(ctx.imports),
                List.copyOf(ctx.methodCalls),
                List.copyOf(ctx.fileAnnotations));
    }

    private static void walkWithContext(TSNode node, Context ctx) {
        String type = node.getType();
        switch (type) {
            case "package_declaration" -> ctx.packageName = extractPackage(node, ctx.source);
            case "import_declaration" -> ctx.imports.add(extractImport(node, ctx.source));
            case "class_declaration" -> {
                ParsedType parsed = extractType(node, GraphNodeType.CLASS, ctx.source);
                ctx.types.add(parsed);
                ctx.typeStack.push(parsed.name());
                walkChildren(node, ctx);
                ctx.typeStack.pop();
                return;
            }
            case "interface_declaration" -> {
                ParsedType parsed = extractType(node, GraphNodeType.INTERFACE, ctx.source);
                ctx.types.add(parsed);
                ctx.typeStack.push(parsed.name());
                walkChildren(node, ctx);
                ctx.typeStack.pop();
                return;
            }
            case "enum_declaration" -> {
                ParsedType parsed = extractType(node, GraphNodeType.ENUM, ctx.source);
                ctx.types.add(parsed);
                ctx.typeStack.push(parsed.name());
                walkChildren(node, ctx);
                ctx.typeStack.pop();
                return;
            }
            case "method_declaration", "constructor_declaration" -> {
                String methodName = extractMethodName(node, ctx.source);
                ctx.methodStack.push(methodName);
                walkChildren(node, ctx);
                ctx.methodStack.pop();
                return;
            }
            case "method_invocation" -> ctx.methodCalls.add(extractMethodCall(node, ctx));
            default -> walkChildren(node, ctx);
        }
    }

    private static void walkChildren(TSNode node, Context ctx) {
        for (int i = 0; i < node.getChildCount(); i++) {
            walkWithContext(node.getChild(i), ctx);
        }
    }

    private static String extractPackage(TSNode node, String source) {
        String content = TreeSitterNodes.text(node, source);
        if (content.startsWith("package ")) {
            content = content.substring(8);
        }
        if (content.endsWith(";")) {
            content = content.substring(0, content.length() - 1);
        }
        return content.trim();
    }

    private static ParsedImport extractImport(TSNode node, String source) {
        String content = TreeSitterNodes.text(node, source);
        boolean isStatic = content.contains("import static");
        String imported = content
                .replace("import static", "")
                .replace("import", "")
                .replace(";", "")
                .trim();
        return new ParsedImport(imported, isStatic, TreeSitterNodes.line(node));
    }

    private static ParsedType extractType(TSNode node, GraphNodeType kind, String source) {
        String name = TreeSitterNodes.firstIdentifier(node, source);
        TSNode modifiers = TreeSitterNodes.findChildByType(node, "modifiers");
        List<String> annotations = modifiers != null
                ? TreeSitterNodes.collectAnnotations(modifiers, source)
                : List.of();

        String superClass = "";
        TSNode superclassNode = TreeSitterNodes.findChildByType(node, "superclass");
        if (superclassNode != null) {
            superClass = TreeSitterNodes.firstTypeName(superclassNode, source);
            if (superClass.startsWith("extends ")) {
                superClass = superClass.substring(8).trim();
            }
        }

        List<String> interfaces = new ArrayList<>();
        TSNode interfacesNode = TreeSitterNodes.findChildByType(node, "super_interfaces");
        if (interfacesNode != null) {
            TreeSitterNodes.walk(interfacesNode, source, child -> {
                if ("type_identifier".equals(child.getType()) || "scoped_identifier".equals(child.getType())) {
                    interfaces.add(TreeSitterNodes.text(child, source));
                }
            });
        }

        return new ParsedType(
                name,
                kind,
                superClass,
                List.copyOf(interfaces),
                extractMethods(node, source),
                annotations,
                TreeSitterNodes.line(node),
                TreeSitterNodes.line(node) + countLines(node));
    }

    private static List<ParsedMethod> extractMethods(TSNode typeNode, String source) {
        List<ParsedMethod> methods = new ArrayList<>();
        TreeSitterNodes.walk(typeNode, source, node -> {
            if ("method_declaration".equals(node.getType()) || "constructor_declaration".equals(node.getType())) {
                methods.add(extractMethod(node, source));
            }
        });
        return methods;
    }

    private static ParsedMethod extractMethod(TSNode node, String source) {
        String name = extractMethodName(node, source);

        String returnType = "void";
        for (int i = 0; i < node.getChildCount(); i++) {
            TSNode child = node.getChild(i);
            String childType = child.getType();
            if ("type_identifier".equals(childType)
                    || "scoped_type_identifier".equals(childType)
                    || "void_type".equals(childType)) {
                returnType = TreeSitterNodes.text(child, source);
                break;
            }
        }

        List<String> parameters = new ArrayList<>();
        TSNode params = TreeSitterNodes.findChildByType(node, "formal_parameters");
        if (params != null) {
            for (int i = 0; i < params.getChildCount(); i++) {
                TSNode child = params.getChild(i);
                if ("formal_parameter".equals(child.getType()) || "spread_parameter".equals(child.getType())) {
                    parameters.add(TreeSitterNodes.text(child, source));
                }
            }
        }

        TSNode modifiers = TreeSitterNodes.findChildByType(node, "modifiers");
        List<String> annotations = modifiers != null
                ? TreeSitterNodes.collectAnnotations(modifiers, source)
                : List.of();

        return new ParsedMethod(
                name,
                returnType,
                List.copyOf(parameters),
                annotations,
                TreeSitterNodes.line(node),
                TreeSitterNodes.line(node) + 1);
    }

    private static String extractMethodName(TSNode node, String source) {
        String name = TreeSitterNodes.firstIdentifier(node, source);
        if (name.isEmpty() && "constructor_declaration".equals(node.getType())) {
            TSNode parent = node.getParent();
            while (parent != null && !parent.isNull()) {
                if ("class_declaration".equals(parent.getType())
                        || "enum_declaration".equals(parent.getType())) {
                    return TreeSitterNodes.firstIdentifier(parent, source);
                }
                parent = parent.getParent();
            }
        }
        return name;
    }

    private static ParsedMethodCall extractMethodCall(TSNode node, Context ctx) {
        String methodName = "";
        String targetType = "";
        List<TSNode> identifiers = new ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) {
            TSNode child = node.getChild(i);
            String childType = child.getType();
            if ("field_access".equals(childType)) {
                targetType = TreeSitterNodes.text(child, ctx.source);
            } else if ("identifier".equals(childType)) {
                identifiers.add(child);
            }
        }
        if (identifiers.size() >= 2) {
            targetType = TreeSitterNodes.text(identifiers.get(0), ctx.source);
            methodName = TreeSitterNodes.text(identifiers.get(identifiers.size() - 1), ctx.source);
        } else if (identifiers.size() == 1) {
            methodName = TreeSitterNodes.text(identifiers.get(0), ctx.source);
        }

        return new ParsedMethodCall(
                ctx.currentType(),
                ctx.currentMethod(),
                targetType,
                methodName,
                TreeSitterNodes.line(node));
    }

    private static int countLines(TSNode node) {
        return Math.max(1, node.getEndPoint().getRow() - node.getStartPoint().getRow() + 1);
    }

    private static final class Context {
        final String source;
        String packageName = "";
        final List<ParsedType> types = new ArrayList<>();
        final List<ParsedImport> imports = new ArrayList<>();
        final List<ParsedMethodCall> methodCalls = new ArrayList<>();
        final Set<String> fileAnnotations = new LinkedHashSet<>();
        final Deque<String> typeStack = new ArrayDeque<>();
        final Deque<String> methodStack = new ArrayDeque<>();

        Context(String source) {
            this.source = source;
        }

        String currentType() {
            return typeStack.isEmpty() ? "" : typeStack.peek();
        }

        String currentMethod() {
            return methodStack.isEmpty() ? "" : methodStack.peek();
        }
    }
}
