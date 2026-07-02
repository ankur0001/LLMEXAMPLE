package com.projectmind.adapter.parser.treesitter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.treesitter.TSLanguage;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TSNode;
import org.treesitter.TreeSitterJava;
import org.treesitter.TreeSitterJson;
import org.treesitter.TreeSitterKotlin;
import org.treesitter.TreeSitterYaml;

/**
 * Loads Tree-sitter native grammars (bonede tree-sitter-ng) and provides parse helpers.
 */
public final class TreeSitterRuntime {

    private static final Logger log = LoggerFactory.getLogger(TreeSitterRuntime.class);
    private static volatile boolean loaded;

    public enum Grammar {
        JAVA, KOTLIN, YAML, JSON
    }

    public record ParseResult(TSTree tree, String source) {
        public TSNode root() {
            return tree.getRootNode();
        }
    }

    private TreeSitterRuntime() {
    }

    public static void ensureLoaded() {
        if (!loaded) {
            synchronized (TreeSitterRuntime.class) {
                if (!loaded) {
                    new TreeSitterJava();
                    loaded = true;
                    log.debug("Tree-sitter native library loaded");
                }
            }
        }
    }

    public static ParseResult parse(Grammar grammar, String source) {
        ensureLoaded();
        TSParser parser = new TSParser();
        TSLanguage language = switch (grammar) {
            case JAVA -> new TreeSitterJava();
            case KOTLIN -> new TreeSitterKotlin();
            case YAML -> new TreeSitterYaml();
            case JSON -> new TreeSitterJson();
        };
        if (!parser.setLanguage(language)) {
            throw new TreeSitterParseException("Failed to set Tree-sitter language: " + grammar);
        }
        try {
            TSTree tree = parser.parseString(null, source);
            if (tree == null) {
                throw new TreeSitterParseException("Tree-sitter returned null tree for " + grammar);
            }
            return new ParseResult(tree, source);
        } catch (TreeSitterParseException ex) {
            throw ex;
        } catch (Exception e) {
            throw new TreeSitterParseException("Failed to parse with " + grammar, e);
        }
    }

    public static boolean isSupportedPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("mac") || os.contains("linux");
    }

    public static boolean isNativeAvailable() {
        if (!isSupportedPlatform()) {
            return false;
        }
        try {
            ensureLoaded();
            return true;
        } catch (Throwable t) {
            log.debug("Tree-sitter native library unavailable: {}", t.getMessage());
            return false;
        }
    }
}
