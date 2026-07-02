package com.projectmind.core.graph;

/**
 * Stable identifiers for knowledge graph nodes.
 */
public final class GraphNodeIds {

    private GraphNodeIds() {
    }

    public static String packageId(String packageName) {
        return "pkg:" + packageName;
    }

    public static String typeId(String packageName, String typeName) {
        if (packageName == null || packageName.isBlank()) {
            return "type:" + typeName;
        }
        return "type:" + packageName + "." + typeName;
    }

    public static String importTargetId(String importedName) {
        return "import:" + importedName;
    }

    public static String configFileId(String relativePath) {
        return "config:" + relativePath;
    }

    public static String configKeyId(String relativePath, String key) {
        return "config:" + relativePath + "#" + key;
    }

    public static String databaseId(String name) {
        return "db:" + name;
    }

    public static String mavenModuleId(String relativePath) {
        return "module:" + relativePath;
    }
}
