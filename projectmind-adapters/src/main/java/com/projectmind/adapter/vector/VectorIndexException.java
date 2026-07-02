package com.projectmind.adapter.vector;

/**
 * Thrown when vector indexing fails.
 */
public class VectorIndexException extends RuntimeException {

    public VectorIndexException(String message) {
        super(message);
    }

    public VectorIndexException(String message, Throwable cause) {
        super(message, cause);
    }
}
