package com.truyengg.model.dto;

import com.truyengg.model.response.ChapterCrawlProgress;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class CrawlState {
  private final Map<String, ChapterCrawlProgress> chapterCrawlProgress;
  private final List<String> resumeImageUrls;
  private int totalDownloadedImages;
  private int totalImages;
  private int downloadedChapters;
}
