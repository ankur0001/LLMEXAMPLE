package com.projectmind.core.domain;

import java.util.function.Consumer;

/**
 * Callback for reporting progress during long-running operations.
 */
@FunctionalInterface
public interface ProgressCallback {
    void onProgress(String phase, int current, int total, String message);

    static ProgressCallback noop() {
        return (phase, current, total, message) -> {};
    }

    static ProgressCallback logging(Consumer<String> logger) {
        return (phase, current, total, message) ->
                logger.accept(String.format("[%s] %d/%d - %s", phase, current, total, message));
    }
}
