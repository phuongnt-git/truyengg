package com.truyengg.model.dto;

import com.truyengg.model.response.ChapterCrawlProgress;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PauseContextParams(
    UUID crawlId,
    List<String> crawledChapters,
    Map<String, ChapterCrawlProgress> chapterCrawlProgress,
    int totalChapters,
    int totalDownloadedImages,
    int totalImages,
    List<String> messages,
    ZonedDateTime startTime) {
}

