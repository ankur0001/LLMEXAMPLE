package com.projectmind.core.port;

import java.nio.file.Path;

/**
 * Schedules post-scan work such as per-file summarization and HTML documentation.
 */
public interface PostScanEnrichmentPort {

    void scheduleAfterScan(Path repositoryPath);
}
