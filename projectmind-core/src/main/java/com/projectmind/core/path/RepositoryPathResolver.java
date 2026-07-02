package com.projectmind.core.path;

import java.nio.file.Path;

/**
 * Canonicalizes repository paths so scan, status, and memory lookups use the same location.
 */
public final class RepositoryPathResolver {

    private RepositoryPathResolver() {
    }

    public static Path resolve(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("Repository path is required");
        }
        return resolve(Path.of(expandHome(rawPath.trim())));
    }

    public static Path resolve(Path repositoryPath) {
        Path path = repositoryPath;
        if (!path.isAbsolute()) {
            path = path.toAbsolutePath();
        }
        path = path.normalize();
        try {
            if (java.nio.file.Files.exists(path)) {
                return path.toRealPath();
            }
        } catch (java.io.IOException ignored) {
            // Fall back to normalized absolute path when the repo does not exist yet.
        }
        return path;
    }

    public static String toStorageKey(Path repositoryPath) {
        return resolve(repositoryPath).toString();
    }

    private static String expandHome(String path) {
        if (path.equals("~")) {
            return System.getProperty("user.home");
        }
        if (path.startsWith("~/") || path.startsWith("~\\")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }
}
