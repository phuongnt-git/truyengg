package com.truyengg.service.crawl;

import com.truyengg.domain.entity.CategoryCrawlDetail;
import com.truyengg.domain.entity.ComicCrawl;
import com.truyengg.domain.enums.ComicCrawlStatus;
import com.truyengg.domain.enums.DownloadMode;
import com.truyengg.domain.repository.CategoryCrawlJobRepository;
import com.truyengg.domain.repository.ComicCrawlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static java.time.ZonedDateTime.now;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryCrawlErrorHandler {

  private final ComicCrawlRepository comicCrawlRepository;
  private final CategoryCrawlJobRepository categoryCrawlJobRepository;

  @Transactional
  public ComicCrawl handleStoryFailure(CategoryCrawlDetail detail, Exception error, UUID categoryJobId) {
    log.warn("Handling story failure for: {} - Error: {}", detail.getStoryUrl(), error.getMessage());

    var categoryJob = detail.getCategoryCrawlJob();
    var crawlJob = ComicCrawl.builder()
        .status(ComicCrawlStatus.FAILED)
        .url(detail.getStoryUrl())
        .downloadMode(DownloadMode.FULL) // Full story
        .totalChapters(0)
        .downloadedChapters(0)
        .startTime(now())
        .createdBy(categoryJob.getCreatedBy())
        .message("Failed during category crawl: " + categoryJobId + " - " + error.getMessage())
        .build();

    var savedCrawl = comicCrawlRepository.save(crawlJob);
    log.info("Created failed crawl {} for story: {}", savedCrawl.getId(), detail.getStoryUrl());
    return savedCrawl;
  }

  @Transactional
  public ComicCrawl handleChapterFailure(CategoryCrawlDetail detail, List<String> failedChapterUrls,
                                         UUID categoryJobId) {
    log.warn("Handling chapter failure for: {} - Failed chapters: {}", detail.getStoryUrl(), failedChapterUrls.size());

    var categoryJob = detail.getCategoryCrawlJob();
    // For partial failure, we need to determine chapter range
    // This is a simplified version - in practice, you'd need to map URLs to chapter indices
    var crawlJob = ComicCrawl.builder()
        .status(ComicCrawlStatus.FAILED)
        .url(detail.getStoryUrl())
        .downloadMode(DownloadMode.PARTIAL) // Partial
        .partStart(1) // Will need to be calculated based on failed chapters
        .partEnd(failedChapterUrls.size())
        .totalChapters(failedChapterUrls.size())
        .downloadedChapters(0)
        .startTime(now())
        .createdBy(categoryJob.getCreatedBy())
        .message("Failed chapters during category crawl: " + categoryJobId + " - " + failedChapterUrls.size() + " chapters failed")
        .build();

    var savedCrawl = comicCrawlRepository.save(crawlJob);
    log.info("Created failed crawl {} for partial story: {}", savedCrawl.getId(), detail.getStoryUrl());
    return savedCrawl;
  }

  @Transactional
  public ComicCrawl createFailedCrawlJob(String storyUrl, List<String> failedChapters, UUID categoryJobId,
                                         String source) {
    var categoryJob = categoryCrawlJobRepository.findById(categoryJobId)
        .orElseThrow(() -> new IllegalArgumentException("Category crawl job not found: " + categoryJobId));

    if (failedChapters == null || failedChapters.isEmpty()) {
      // Full story failure
      return createFullStoryFailedJob(storyUrl, categoryJobId, source, categoryJob.getCreatedBy());
    } else {
      // Partial failure
      return createPartialStoryFailedJob(storyUrl, failedChapters, categoryJobId, source, categoryJob.getCreatedBy());
    }
  }

  private ComicCrawl createFullStoryFailedJob(String storyUrl, UUID categoryJobId, String source,
                                              com.truyengg.domain.entity.User createdBy) {
    var crawlJob = ComicCrawl.builder()
        .status(ComicCrawlStatus.FAILED)
        .url(storyUrl)
        .downloadMode(DownloadMode.FULL)
        .totalChapters(0)
        .downloadedChapters(0)
        .startTime(now())
        .createdBy(createdBy)
        .message("Category crawl: " + categoryJobId + " - Story failed during category crawl")
        .build();

    return comicCrawlRepository.save(crawlJob);
  }

  private ComicCrawl createPartialStoryFailedJob(String storyUrl, List<String> failedChapters,
                                                 UUID categoryJobId, String source,
                                                 com.truyengg.domain.entity.User createdBy) {
    var crawlJob = ComicCrawl.builder()
        .status(ComicCrawlStatus.FAILED)
        .url(storyUrl)
        .downloadMode(DownloadMode.PARTIAL)
        .partStart(1)
        .partEnd(failedChapters.size())
        .totalChapters(failedChapters.size())
        .downloadedChapters(0)
        .startTime(now())
        .createdBy(createdBy)
        .message("Category crawl: " + categoryJobId + " - Story failed during category crawl")
        .build();

    return comicCrawlRepository.save(crawlJob);
  }
}

