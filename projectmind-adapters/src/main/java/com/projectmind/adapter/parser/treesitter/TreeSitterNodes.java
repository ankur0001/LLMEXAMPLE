package com.projectmind.adapter.parser.treesitter;

import org.treesitter.TSNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Utility methods for walking Tree-sitter syntax trees (bonede TSNode API).
 */
final class TreeSitterNodes {

    private TreeSitterNodes() {
    }

    static void walk(TSNode root, String source, Consumer<TSNode> visitor) {
        walkRecursive(root, source, visitor);
    }

    private static void walkRecursive(TSNode node, String source, Consumer<TSNode> visitor) {
        if (node == null || node.isNull()) {
            return;
        }
        visitor.accept(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            walkRecursive(node.getChild(i), source, visitor);
        }
    }

    static List<TSNode> findByType(TSNode root, String source, String type) {
        List<TSNode> matches = new ArrayList<>();
        walk(root, source, node -> {
            if (type.equals(node.getType())) {
                matches.add(node);
            }
        });
        return matches;
    }

    static String text(TSNode node, String source) {
        if (node == null || node.isNull() || source == null) {
            return "";
        }
        int start = node.getStartByte();
        int end = node.getEndByte();
        if (start < 0 || end > source.length() || start >= end) {
            return "";
        }
        return source.substring(start, end).trim();
    }

    static int line(TSNode node) {
        if (node == null || node.isNull()) {
            return 0;
        }
        return node.getStartPoint().getRow() + 1;
    }

    static String firstIdentifier(TSNode node, String source) {
        for (int i = 0; i < node.getChildCount(); i++) {
            TSNode child = node.getChild(i);
            if ("identifier".equals(child.getType())) {
                return text(child, source);
            }
        }
        return "";
    }

    static String firstTypeName(TSNode node, String source) {
        for (int i = 0; i < node.getChildCount(); i++) {
            TSNode child = node.getChild(i);
            String type = child.getType();
            if ("type_identifier".equals(type) || "scoped_identifier".equals(type) || "identifier".equals(type)) {
                return text(child, source);
            }
        }
        return text(node, source);
    }

    static List<String> collectAnnotations(TSNode modifiersNode, String source) {
        List<String> annotations = new ArrayList<>();
        if (modifiersNode == null || modifiersNode.isNull()) {
            return annotations;
        }
        for (int i = 0; i < modifiersNode.getChildCount(); i++) {
            TSNode child = modifiersNode.getChild(i);
            String type = child.getType();
            if ("annotation".equals(type) || "marker_annotation".equals(type)) {
                annotations.add(text(child, source));
            }
        }
        return annotations;
    }

    static TSNode findChildByType(TSNode node, String type) {
        for (int i = 0; i < node.getChildCount(); i++) {
            TSNode child = node.getChild(i);
            if (type.equals(child.getType())) {
                return child;
            }
        }
        return null;
    }
}
