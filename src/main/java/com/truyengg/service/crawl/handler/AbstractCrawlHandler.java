package com.truyengg.service.crawl.handler;

import com.truyengg.domain.entity.CrawlJob;
import com.truyengg.domain.entity.CrawlSettings;
import com.truyengg.domain.enums.CrawlType;
import com.truyengg.domain.repository.CrawlSettingsRepository;
import com.truyengg.service.crawl.CrawlCheckpointService;
import com.truyengg.service.crawl.CrawlHttpClient;
import com.truyengg.service.crawl.CrawlJobService;
import com.truyengg.service.crawl.CrawlProgressService;
import com.truyengg.service.crawl.CrawlQueueProcessor;
import com.truyengg.service.crawl.DownloadModeService;
import com.truyengg.service.crawl.PauseStateService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;

import static com.truyengg.domain.enums.CrawlStatus.CANCELLED;
import static com.truyengg.domain.enums.CrawlStatus.PAUSED;
import static com.truyengg.domain.exception.CrawlException.cancelled;
import static com.truyengg.domain.exception.CrawlException.paused;
import static com.truyengg.service.crawl.SlugExtractor.extractSlugFromUrl;
import static java.lang.Boolean.TRUE;
import static java.lang.Math.min;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Abstract base class for crawl type handlers.
 * Provides common functionality for pause/cancel checking, progress updates, and checkpoint management.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public abstract class AbstractCrawlHandler implements CrawlTypeHandler {

  CrawlJobService jobService;
  CrawlProgressService progressService;
  CrawlCheckpointService checkpointService;
  CrawlHttpClient httpClient;
  CrawlHandlerFactory handlerFactory;
  PauseStateService pauseStateService;
  DownloadModeService downloadModeService;
  CrawlSettingsRepository settingsRepository;
  CrawlQueueProcessor queueProcessor;

  @Override
  public void handle(CrawlJob job) {
    handleWithResume(job, 0);
  }

  // ===== Pause/Cancel Control =====

  /**
   * Check if the job is paused or cancelled, and throw appropriate exception if so.
   * Call this at the beginning of each item processing loop iteration.
   */
  protected void checkPauseOrCancel(UUID jobId, int lastIndex) {
    // Check in-memory state first (faster)
    if (TRUE.equals(pauseStateService.isPaused(jobId))) {
      throw paused(jobId, lastIndex);
    }

    if (TRUE.equals(pauseStateService.isCancelled(jobId))) {
      throw cancelled(jobId);
    }

    // Check database state (authoritative)
    var jobOpt = jobService.findById(jobId);
    if (jobOpt.isPresent()) {
      var job = jobOpt.get();
      if (job.getStatus() == PAUSED) {
        pauseStateService.setPaused(jobId);
        throw paused(jobId, lastIndex);
      }
      if (job.getStatus() == CANCELLED) {
        pauseStateService.setCancelled(jobId);
        throw cancelled(jobId);
      }
    }
  }

  // ===== Progress Tracking =====

  /**
   * Update progress for the current item.
   */
  protected void updateProgress(UUID jobId, int currentIndex, int totalItems, String currentItemName) {
    progressService.updateProgress(jobId, currentIndex, totalItems, currentItemName);
  }

  /**
   * Update progress message.
   */
  protected void updateMessage(UUID jobId, String message) {
    progressService.updateMessage(jobId, message);
  }

  /**
   * Update total items count for the job.
   */
  protected void setTotalItems(UUID jobId, int totalItems) {
    var job = jobService.getById(jobId);
    job.setTotalItems(totalItems);
    progressService.setTotalItems(jobId, totalItems);
  }

  /**
   * Increment completed items counter.
   */
  protected void incrementCompleted(UUID jobId) {
    progressService.incrementCompleted(jobId);
  }

  /**
   * Increment failed items counter.
   */
  protected void incrementFailed(UUID jobId) {
    progressService.incrementFailed(jobId);
  }

  /**
   * Increment skipped items counter.
   */
  protected void incrementSkipped(UUID jobId) {
    progressService.incrementSkipped(jobId);
  }

  // ===== Checkpoint Management =====

  /**
   * Save checkpoint at the current position.
   */
  protected void saveCheckpoint(UUID jobId, int lastIndex) {
    checkpointService.updateLastItemIndex(jobId, lastIndex);
  }

  // ===== Queue Operations =====

  /**
   * Enqueue items to the queue and trigger processing.
   * This is the unified way to create child items for processing.
   */
  protected void enqueueAndProcess(UUID jobId, CrawlType type, List<String> urls, List<String> names) {
    queueProcessor.enqueueAndProcess(jobId, type, urls, names);
  }

  // ===== Range & Skip Settings =====

  /**
   * Fetch settings for a job using @MapsId (shared PK).
   */
  protected CrawlSettings getSettings(UUID jobId) {
    return settingsRepository.findById(jobId).orElse(null);
  }

  /**
   * Get list of item indices to download based on job's download mode and settings.
   * Delegates to DownloadModeService for proper filtering (FULL/UPDATE/PARTIAL/NONE).
   */
  protected List<Integer> getItemsToDownload(CrawlJob job, List<String> itemUrls) {
    return downloadModeService.determineItemsToDownload(job, itemUrls);
  }

  /**
   * Get effective start index from settings.
   * Converts 1-based setting to 0-based index.
   */
  protected int getEffectiveStart(CrawlSettings settings) {
    if (settings == null) return 0;
    return settings.getRangeStart() > 0 ? settings.getRangeStart() - 1 : 0;
  }

  /**
   * Get effective end index from settings.
   */
  protected int getEffectiveEnd(CrawlSettings settings, int total) {
    if (settings == null) return total;
    return settings.getRangeEnd() > 0 ? min(settings.getRangeEnd(), total) : total;
  }

  /**
   * Check if an item should be skipped based on skip list in settings.
   * Note: skipItems uses 1-based indices.
   */
  protected boolean shouldSkipItem(int index, CrawlSettings settings) {
    if (settings == null) return false;
    var skipItems = settings.getSkipItems();
    if (!isNotEmpty(skipItems)) return false;
    return skipItems.contains(index + 1); // Convert to 1-based
  }

  // ===== Slug Extraction =====

  /**
   * Extract comic slug from job hierarchy.
   * Tries: job's targetSlug → extracted from URL → parent's slug → root's slug → "unknown"
   */
  protected String extractSlug(CrawlJob job) {
    // Try job's own slug
    if (isNotBlank(job.getTargetSlug())) {
      return job.getTargetSlug();
    }

    // Try extract from URL
    var slug = extractSlugFromUrl(job.getTargetUrl());
    if (isNotBlank(slug)) {
      return slug;
    }

    // Try parent job
    if (job.getParentJob() != null && isNotBlank(job.getParentJob().getTargetSlug())) {
      return job.getParentJob().getTargetSlug();
    }

    // Try root job
    if (job.getRootJob() != null && isNotBlank(job.getRootJob().getTargetSlug())) {
      return job.getRootJob().getTargetSlug();
    }

    return "unknown";
  }
}
