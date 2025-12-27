package com.truyengg.model.dto;

import java.time.ZonedDateTime;

public record ChapterImageInfo(
    Long id,
    Long chapterId,
    String path,
    String originalUrl,
    Integer imageOrder,
    Integer manualOrder,
    Boolean isDownloaded,
    Boolean isVisible,
    String blurhash,
    ZonedDateTime deletedAt,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
}
