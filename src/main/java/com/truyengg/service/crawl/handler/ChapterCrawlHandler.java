package com.truyengg.service.crawl.handler;

import com.truyengg.domain.entity.CrawlJob;
import com.truyengg.domain.enums.CrawlType;
import com.truyengg.domain.repository.CrawlSettingsRepository;
import com.truyengg.model.dto.ChapterCrawlProcessingParams;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.truyengg.domain.enums.CrawlSourceType.detectSourceFromUrl;
import static com.truyengg.domain.enums.CrawlType.CHAPTER;
import static com.truyengg.domain.enums.CrawlType.IMAGE;
import static com.truyengg.domain.enums.DownloadMode.NONE;
import static java.lang.Math.max;
import static java.util.Collections.singletonList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;

/**
 * Handler for CHAPTER type crawl jobs.
 * Extracts image URLs from a chapter page and enqueues IMAGE items for processing.
 * Uses unified queue-based pattern like other handlers.
 */
@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChapterCrawlHandler extends AbstractCrawlHandler {

  public ChapterCrawlHandler(
      CrawlJobService jobService,
      CrawlProgressService progressService,
      CrawlCheckpointService checkpointService,
      CrawlHttpClient httpClient,
      CrawlHandlerFactory handlerFactory,
      PauseStateService pauseStateService,
      DownloadModeService downloadModeService,
      CrawlSettingsRepository settingsRepository,
      CrawlQueueProcessor queueProcessor) {
    super(jobService, progressService, checkpointService, httpClient, handlerFactory, pauseStateService, downloadModeService, settingsRepository, queueProcessor);
  }

  @Override
  public void handleWithResume(CrawlJob job, int startIndex) {
    var jobId = job.getId();

    var sourceType = detectSourceFromUrl(job.getTargetUrl());
    var handler = handlerFactory.getHandler(sourceType);
    var domain = handler.extractDomainFromUrl(job.getTargetUrl());

    updateMessage(jobId, "Extracting image URLs from chapter...");

    // Build chapter params for image extraction
    var params = ChapterCrawlProcessingParams.builder()
        .crawlId(jobId)
        .url(job.getTargetUrl())
        .domain(domain)
        .chapterKey("0")
        .chapterIndex(0)
        .chapterProgress(new HashMap<>())
        .messages(new ArrayList<>())
        .build();

    // Extract image URLs
    var imageUrls = handler.extractImageUrls(params);

    if (isEmpty(imageUrls)) {
      updateMessage(jobId, "No images found in chapter");
      return;
    }

    // Apply range settings
    var settings = getSettings(jobId);
    var effectiveStart = max(startIndex, getEffectiveStart(settings));
    var effectiveEnd = getEffectiveEnd(settings, imageUrls.size());
    var totalImages = effectiveEnd - effectiveStart;

    setTotalItems(jobId, totalImages);
    updateMessage(jobId, "Found " + imageUrls.size() + " images. Processing " + totalImages);

    // Handle download mode NONE - skip creating child jobs
    if (job.getDownloadMode() == NONE) {
      updateMessage(jobId, "Download mode is NONE - skipping image download");
      return;
    }

    // Store image URLs in checkpoint for reference
    storeImageUrls(jobId, imageUrls);

    // Process each image one-by-one using unified queue pattern
    var enqueuedCount = 0;
    for (var imageIndex = effectiveStart; imageIndex < effectiveEnd; imageIndex++) {
      checkPauseOrCancel(jobId, imageIndex);

      var imageUrl = imageUrls.get(imageIndex);
      var imageName = "Image #" + (imageIndex + 1);

      updateProgress(jobId, enqueuedCount, totalImages, imageName);

      try {
        if (shouldSkipItem(imageIndex, settings)) {
          incrementSkipped(jobId);
          continue;
        }

        // Enqueue single image for processing (will create IMAGE job in CrawlQueueProcessor)
        enqueueAndProcess(jobId, IMAGE, singletonList(imageUrl), singletonList(imageName));
        incrementCompleted(jobId);
        saveCheckpoint(jobId, imageIndex);
        enqueuedCount++;

      } catch (Exception e) {
        log.warn("Failed to enqueue image {}: {}", imageUrl, getRootCauseMessage(e));
        incrementFailed(jobId);
        checkpointService.addFailedIndex(jobId, imageIndex);
      }
    }

    updateMessage(jobId, "Chapter crawl completed - enqueued " + enqueuedCount + " images");
  }

  @Override
  public CrawlType getSupportedType() {
    return CHAPTER;
  }

  // ===== Private methods =====

  /**
   * Store image URLs in parent job's checkpoint for reference.
   */
  private void storeImageUrls(java.util.UUID jobId, List<String> imageUrls) {
    var metadata = imageUrls.stream()
        .map(url -> Map.of("url", url))
        .toList();
    checkpointService.saveState(jobId, "imageUrls", metadata);
  }
}
