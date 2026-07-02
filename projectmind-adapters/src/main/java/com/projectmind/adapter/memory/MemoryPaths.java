package com.projectmind.adapter.memory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Path and naming helpers for on-disk memory artifacts.
 */
final class MemoryPaths {

    private MemoryPaths() {
    }

    static String safeFileName(String value) {
        return value.replace('/', '_').replace('\\', '_').replace(':', '_');
    }

    static String cacheFileName(String key) {
        return safeHash(key) + ".json";
    }

    static String historyFileName(long epochMillis, String operation) {
        String safeOperation = operation.replaceAll("[^a-zA-Z0-9_-]", "_");
        return epochMillis + "_" + safeOperation + ".json";
    }

    private static String safeHash(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return safeFileName(key);
        }
    }
}
