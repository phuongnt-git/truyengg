package com.truyengg.model.response;

import java.time.ZonedDateTime;

public record HistoryResponse(
    Long id,
    Long comicId,
    String comicSlug,
    String comicName,
    String thumbUrl,
    String chapterName,
    ZonedDateTime lastReadAt
) {
}
