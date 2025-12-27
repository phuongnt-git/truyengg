package com.truyengg.model.dto;

/**
 * Cleanup statistics.
 */
public record CleanupStats(
    int retentionDays,
    boolean autoCleanupEnabled,
    boolean cleanupStorage,
    int pendingCleanupCount
) {
}
