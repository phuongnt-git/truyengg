package com.truyengg.model.response;

import com.truyengg.domain.enums.ChapterCrawlStatus;

public record ChapterCrawlProgress(
    int chapterIndex,
    String chapterUrl,
    int downloadedImages,
    int totalImages,
    ChapterCrawlStatus status
) {
}
