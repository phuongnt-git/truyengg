package com.truyengg.model.response;

import java.util.List;

public record CrawlResponse(
    boolean success,
    List<String> messages,
    Integer totalChapters,
    Integer downloadedChapters
) {
}

