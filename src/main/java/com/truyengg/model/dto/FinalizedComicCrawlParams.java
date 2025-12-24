package com.truyengg.model.dto;

import com.truyengg.domain.enums.ComicCrawlStatus;
import com.truyengg.model.response.ChapterCrawlProgress;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record FinalizedComicCrawlParams(
    UUID crawlId,
    ComicCrawlStatus status,
    int totalChapters,
    int downloadedChapters,
    List<String> messages,
    ZonedDateTime startTime,
    Map<String, ChapterCrawlProgress> chapterProgress
) {
}
