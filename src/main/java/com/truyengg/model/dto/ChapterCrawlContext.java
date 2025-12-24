package com.truyengg.model.dto;

import com.truyengg.domain.entity.Comic;
import com.truyengg.model.response.ChapterCrawlProgress;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ChapterCrawlContext(
    UUID crawlId,
    List<String> crawledChapters,
    Map<String, ChapterCrawlProgress> chapterCrawlProgressMap,
    int totalChapters,
    int totalDownloadedImages,
    int totalImages,
    List<String> messages,
    ZonedDateTime startTime,
    Comic comic) {
}

