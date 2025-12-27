package com.truyengg.service.crawl.handler;

import com.truyengg.domain.entity.Comic;
import com.truyengg.domain.entity.CrawlJob;
import com.truyengg.domain.enums.CrawlType;
import com.truyengg.domain.repository.CrawlSettingsRepository;
import com.truyengg.service.comic.ComicService;
import com.truyengg.service.crawl.CrawlCheckpointService;
import com.truyengg.service.crawl.CrawlHttpClient;
import com.truyengg.service.crawl.CrawlJobService;
import com.truyengg.service.crawl.CrawlProgressService;
import com.truyengg.service.crawl.CrawlQueueProcessor;
import com.truyengg.service.crawl.DownloadModeService;
import com.truyengg.service.crawl.PauseStateService;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Optional;

import static com.truyengg.domain.enums.CrawlSourceType.detectSourceFromUrl;
import static com.truyengg.domain.enums.CrawlType.CHAPTER;
import static com.truyengg.domain.enums.CrawlType.COMIC;
import static java.lang.Math.max;
import static java.util.Collections.singletonList;
import static java.util.Optional.empty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;
import static org.jsoup.Jsoup.parse;

/**
 * Handler for COMIC type crawl jobs.
 * Extracts chapters from comic page and enqueues them for processing.
 */
@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ComicCrawlHandler extends AbstractCrawlHandler {

  ComicService comicService;

  public ComicCrawlHandler(
      CrawlJobService jobService,
      CrawlProgressService progressService,
      CrawlCheckpointService checkpointService,
      CrawlHttpClient httpClient,
      CrawlHandlerFactory handlerFactory,
      PauseStateService pauseStateService,
      DownloadModeService downloadModeService,
      CrawlSettingsRepository settingsRepository,
      CrawlQueueProcessor queueProcessor,
      ComicService comicService) {
    super(jobService, progressService, checkpointService, httpClient, handlerFactory, pauseStateService, downloadModeService, settingsRepository, queueProcessor);
    this.comicService = comicService;
  }

  @Override
  public void handleWithResume(CrawlJob job, int startIndex) {
    var jobId = job.getId();

    var sourceType = detectSourceFromUrl(job.getTargetUrl());
    var handler = handlerFactory.getHandler(sourceType);
    var domain = handler.extractDomainFromUrl(job.getTargetUrl());

    updateMessage(jobId, "Detecting comic information...");

    // Detect and save comic info
    var comicOpt = detectComic(handler, job.getTargetUrl(), domain);
    if (comicOpt.isPresent()) {
      var comic = comicOpt.get();
      jobService.linkToContent(jobId, comic.getId());
      updateMessage(jobId, "Detected comic: " + comic.getName());
    }

    // Extract chapters
    updateMessage(jobId, "Extracting chapter list...");
    var chapterUrls = handler.extractChapterList(job.getTargetUrl(), domain, new ArrayList<>());

    if (chapterUrls.isEmpty()) {
      updateMessage(jobId, "No chapters found");
      return;
    }

    // Use DownloadModeService to determine which chapters to download
    var itemsToDownload = getItemsToDownload(job, chapterUrls);

    // Apply range settings and resume index
    var settings = getSettings(jobId);
    var effectiveStart = max(startIndex, getEffectiveStart(settings));
    var effectiveEnd = getEffectiveEnd(settings, itemsToDownload.size());

    // Filter items after resume index
    var filteredItems = itemsToDownload.stream()
        .filter(i -> i >= effectiveStart && i < effectiveEnd)
        .toList();

    var totalChapters = filteredItems.size();
    setTotalItems(jobId, totalChapters);
    updateMessage(jobId, "Found " + chapterUrls.size() + " chapters, will process " + totalChapters);

    // Process each chapter one-by-one for proper progress tracking
    var enqueuedCount = 0;
    for (var chapterIndex : filteredItems) {
      checkPauseOrCancel(jobId, chapterIndex);

      var chapterUrl = chapterUrls.get(chapterIndex);
      var chapterName = "Chapter " + (chapterIndex + 1);

      updateProgress(jobId, enqueuedCount, totalChapters, chapterName);

      try {
        if (shouldSkipItem(chapterIndex, settings)) {
          incrementSkipped(jobId);
          continue;
        }

        // Enqueue single chapter for processing
        enqueueAndProcess(jobId, CHAPTER, singletonList(chapterUrl), singletonList(chapterName));
        incrementCompleted(jobId);
        saveCheckpoint(jobId, chapterIndex);
        enqueuedCount++;

      } catch (Exception e) {
        log.warn("Failed to enqueue chapter {}: {}", chapterUrl, getRootCauseMessage(e));
        incrementFailed(jobId);
      }
    }

    updateMessage(jobId, "Comic crawl completed - enqueued " + enqueuedCount + " chapters");
  }

  @Override
  public CrawlType getSupportedType() {
    return COMIC;
  }

  // ===== Private methods =====

  private Optional<Comic> detectComic(CrawlHandler handler, String url, String domain) {
    try {
      if (handler.isHtmlBased()) {
        var headers = httpClient.buildHeaders(domain);
        var htmlContent = httpClient.fetchUrl(url, headers, false);
        if (isNotBlank(htmlContent)) {
          var doc = parse(htmlContent);
          var comicInfo = handler.detectComicInfo(url, doc, null);
          return comicService.createOrUpdateComic(comicInfo);
        }
      }
    } catch (Exception e) {
      log.warn("Failed to detect comic info: {}", getRootCauseMessage(e));
    }
    return empty();
  }
}
