package com.truyengg.model.dto;

import java.util.List;
import java.util.Map;

public record CommicCrawlCheckpointResponse(
    int currentChapterIndex,
    List<String> crawledChapters,
    Map<String, Integer> chapterProgress
) {
}

