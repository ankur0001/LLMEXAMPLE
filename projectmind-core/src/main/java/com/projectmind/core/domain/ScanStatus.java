package com.projectmind.core.domain;

/**
 * Lifecycle status of a repository within ProjectMind.
 */
public enum ScanStatus {
    NOT_SCANNED,
    SCANNING,
    INDEXED,
    UPDATING,
    ERROR,
    INTERRUPTED
}
