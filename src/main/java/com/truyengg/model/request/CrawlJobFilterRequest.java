package com.truyengg.model.request;

import com.truyengg.domain.enums.ComicCrawlStatus;

import java.time.ZonedDateTime;

public record CrawlJobFilterRequest(
    ComicCrawlStatus status,
    String source,
    Long createdBy,
    String search,
    ZonedDateTime fromDate,
    ZonedDateTime toDate,
    Boolean includeDeleted
) {
}

