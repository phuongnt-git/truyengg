package com.truyengg.model.response;

import com.truyengg.domain.enums.ComicCrawlStatus;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.time.Duration.between;
import static java.time.ZonedDateTime.now;
import static java.util.Collections.emptyMap;

public record ComicCrawlProgressResponse(
    UUID crawlId,
    ComicCrawlStatus status,
    int currentChapter,
    int totalChapters,
    int downloadedImages,
    int totalImages,
    Map<String, ChapterCrawlProgress> chapterProgress,
    String currentMessage,
    List<String> messages,
    ZonedDateTime startTime,
    ZonedDateTime lastUpdate,
    Long elapsedSeconds
) {
  public ComicCrawlProgressResponse(
      ComicCrawlProgressResponse response,
      List<String> messages) {
    this(
        response.crawlId(),
        response.status(),
        response.currentChapter(),
        response.totalChapters(),
        response.downloadedImages(),
        response.totalImages(),
        response.chapterProgress(),
        response.currentMessage(),
        messages, response.startTime(),
        response.lastUpdate(),
        response.elapsedSeconds()
    );
  }

  public ComicCrawlProgressResponse(
      UUID crawlId,
      ComicCrawlStatus status,
      String currentMessage,
      List<String> messages,
      ZonedDateTime startTime) {
    this(
        crawlId,
        status,
        0,
        0,
        0,
        0,
        emptyMap(),
        currentMessage,
        messages,
        startTime,
        now(),
        between(startTime, now()).getSeconds()
    );
  }
}

