package com.projectmind.adapter.scanner;

import com.projectmind.core.domain.FileType;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Classifies repository files by type using extension, filename, and path heuristics.
 */
public final class FileTypeDetector {

    private static final Set<String> KUBERNETES_DIR_NAMES = Set.of(
            "k8s", "kubernetes", "kube", "manifests", "manifest", "helm", "charts", "deploy", "deployment");

    public FileTypeDetector() {
    }

    /**
     * Detects the {@link FileType} for a file within a repository.
     */
    public FileType detect(Path absolutePath, Path repositoryRoot) {
        String fileName = absolutePath.getFileName().toString();
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        Path relative = repositoryRoot.relativize(absolutePath);

        if (isDockerFile(lowerName)) {
            return FileType.DOCKER;
        }
        if (isKubernetesManifest(relative, lowerName)) {
            return FileType.KUBERNETES;
        }
        if (isGradleFile(lowerName)) {
            return FileType.GRADLE;
        }
        if (lowerName.endsWith(".java")) {
            return FileType.JAVA;
        }
        if (lowerName.endsWith(".kt") || lowerName.endsWith(".kts")) {
            return FileType.KOTLIN;
        }
        if (isMavenFile(lowerName)) {
            return FileType.MAVEN;
        }
        if (lowerName.endsWith(".yml") || lowerName.endsWith(".yaml")) {
            return FileType.YAML;
        }
        if (lowerName.endsWith(".properties")) {
            return FileType.PROPERTIES;
        }
        if (lowerName.endsWith(".sql")) {
            return FileType.SQL;
        }
        if (lowerName.endsWith(".xml")) {
            return FileType.XML;
        }
        if (lowerName.endsWith(".json")) {
            return FileType.JSON;
        }
        if (lowerName.endsWith(".md") || lowerName.endsWith(".markdown")) {
            return FileType.MARKDOWN;
        }
        if (isShellScript(lowerName)) {
            return FileType.SHELL;
        }
        return FileType.OTHER;
    }

    private boolean isDockerFile(String lowerName) {
        return lowerName.equals("dockerfile")
                || lowerName.startsWith("dockerfile.")
                || lowerName.endsWith(".dockerfile")
                || lowerName.equals("docker-compose.yml")
                || lowerName.equals("docker-compose.yaml")
                || lowerName.startsWith("docker-compose.");
    }

    private boolean isGradleFile(String lowerName) {
        return lowerName.equals("build.gradle")
                || lowerName.equals("settings.gradle")
                || lowerName.endsWith(".gradle")
                || lowerName.endsWith(".gradle.kts");
    }

    private boolean isMavenFile(String lowerName) {
        return lowerName.equals("pom.xml") || lowerName.endsWith(".pom");
    }

    private boolean isShellScript(String lowerName) {
        return lowerName.endsWith(".sh")
                || lowerName.endsWith(".bash")
                || lowerName.endsWith(".zsh")
                || lowerName.equals("gradlew")
                || lowerName.endsWith(".cmd") && lowerName.contains("gradlew");
    }

    private boolean isKubernetesManifest(Path relative, String lowerName) {
        if (!lowerName.endsWith(".yml") && !lowerName.endsWith(".yaml") && !lowerName.endsWith(".json")) {
            return false;
        }
        if (hasKubernetesDirectory(relative)) {
            return true;
        }
        return lowerName.contains("deployment")
                || lowerName.contains("service")
                || lowerName.contains("ingress")
                || lowerName.contains("configmap")
                || lowerName.contains("statefulset")
                || lowerName.contains("daemonset")
                || lowerName.contains("namespace")
                || lowerName.contains("helm");
    }

    private boolean hasKubernetesDirectory(Path relative) {
        for (int i = 0; i < relative.getNameCount() - 1; i++) {
            String segment = relative.getName(i).toString().toLowerCase(Locale.ROOT);
            if (KUBERNETES_DIR_NAMES.contains(segment)) {
                return true;
            }
        }
        return false;
    }
}
