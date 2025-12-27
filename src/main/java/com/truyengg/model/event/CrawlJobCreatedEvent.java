package com.truyengg.model.event;

import java.util.UUID;

/**
 * Event published when a new crawl job is created and ready to be executed.
 */
public record CrawlJobCreatedEvent(UUID jobId) {
}

