package com.projectmind.adapter.parser.treesitter;

/**
 * Thrown when Tree-sitter parsing fails.
 */
public class TreeSitterParseException extends RuntimeException {

    public TreeSitterParseException(String message) {
        super(message);
    }

    public TreeSitterParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
