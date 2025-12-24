package com.truyengg.model.dto;

import com.truyengg.model.request.CrawlRequest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record CrawlRequestParams(
    CrawlRequest request,
    UUID crawlId,
    ZonedDateTime startTime,
    List<String> messages,
    List<String> crawledChapters,
    int resumeFromIndex,
    int resumeFromChapterIndex,
    int resumeFromImageIndex) {
}

