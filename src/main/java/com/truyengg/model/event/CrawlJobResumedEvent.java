package com.truyengg.model.event;

import java.util.UUID;

/**
 * Event published when a paused crawl job is resumed.
 */
public record CrawlJobResumedEvent(UUID jobId, int resumeFromIndex) {
}

