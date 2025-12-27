package com.truyengg.model.response;

import java.time.ZonedDateTime;

public record ChapterResponse(
    Long id,
    Long comicId,
    String comicSlug,
    String chapterName,
    String chapterTitle,
    String source,
    ZonedDateTime createdAt
) {
}

