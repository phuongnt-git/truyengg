package com.truyengg.model.dto;

import java.util.List;

public record StoryCrawlResult(
    boolean success,
    int crawledChapters,
    int failedChapters,
    int totalImages,
    List<String> failedChapterUrls
) {
  public StoryCrawlResult() {
    this(false, 0, 0, 0, List.of());
  }
}
