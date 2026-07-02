package com.projectmind.core.concurrent;

/**
 * Thrown when parallel task execution fails.
 */
public class ParallelExecutionException extends RuntimeException {

    public ParallelExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
