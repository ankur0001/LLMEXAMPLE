package com.projectmind.core.domain;

/**
 * Keys stored in {@link ProjectMetadata#properties()} for post-scan enrichment.
 */
public final class EnrichmentMetadata {

    public static final String STATUS = "enrichment.status";
    public static final String DOCS_PATH = "enrichment.docsPath";
    public static final String FILES_SUMMARIZED = "enrichment.filesSummarized";
    public static final String MESSAGE = "enrichment.message";

    public enum Status {
        PENDING,
        RUNNING,
        READY,
        FAILED
    }

    private EnrichmentMetadata() {
    }
}
