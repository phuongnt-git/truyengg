package com.truyengg.model.dto;

import com.truyengg.domain.enums.CrawlErrorType;

import java.util.UUID;

/**
 * Event published when admin notification is needed.
 */
public record CrawlErrorEvent(
    UUID jobId,
    String targetUrl,
    CrawlErrorType errorType,
    String errorMessage
) {
}
