package com.truyengg.service.crawl;

import com.truyengg.domain.repository.CrawlJobRepository;
import com.truyengg.model.dto.CleanupStats;
import com.truyengg.service.storage.ImageStorageService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.truyengg.service.crawl.SlugExtractor.extractSlugFromUrl;
import static java.time.ZonedDateTime.now;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Service for cleaning up soft-deleted crawl jobs after retention period.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CrawlCleanupService {

  CrawlJobRepository crawlJobRepository;
  CrawlHierarchyService hierarchyService;
  ImageStorageService storageService;

  @NonFinal
  @Value("${crawl.delete.retention-days:30}")
  int retentionDays;

  @NonFinal
  @Value("${crawl.delete.auto-cleanup-enabled:true}")
  boolean autoCleanupEnabled;

  @NonFinal
  @Value("${crawl.delete.cleanup-storage:true}")
  boolean cleanupStorage;

  /**
   * Scheduled job to auto hard-delete old soft-deleted jobs.
   * Runs at 3 AM daily.
   */
  @Scheduled(cron = "${crawl.delete.cleanup-cron:0 0 3 * * ?}")
  @Transactional
  public void autoCleanupOldDeletedJobs() {
    if (!autoCleanupEnabled) {
      return;
    }

    var cutoffDate = now().minusDays(retentionDays);
    var oldDeletedJobs = crawlJobRepository.findSoftDeletedBefore(cutoffDate);

    if (oldDeletedJobs.isEmpty()) {
      return;
    }

    log.info("Starting auto cleanup of {} soft-deleted jobs older than {} days",
        oldDeletedJobs.size(), retentionDays);

    var cleaned = 0;
    var failed = 0;

    for (var job : oldDeletedJobs) {
      try {
        hardDeleteJob(job.getId());
        cleaned++;
      } catch (Exception e) {
        failed++;
        log.warn("Failed to auto-clean job {}: {}",
            job.getId(), ExceptionUtils.getRootCauseMessage(e));
      }
    }

    log.info("Auto cleanup completed: {} cleaned, {} failed", cleaned, failed);
  }

  /**
   * Hard delete a job and cleanup associated storage.
   */
  @Transactional
  public void hardDeleteJob(java.util.UUID jobId) {
    var jobOpt = crawlJobRepository.findByIdIncludeDeleted(jobId);

    if (jobOpt.isEmpty()) {
      return;
    }

    var job = jobOpt.get();
    // Cleanup storage files
    if (cleanupStorage) {
      cleanupStorageFiles(job.getTargetUrl(), job.getTargetSlug());
    }

    // Delete from graph
    hierarchyService.deleteNodeAndDescendants(jobId);

    // Delete from database (CASCADE handles related tables)
    crawlJobRepository.deleteById(jobId);

    log.info("Hard deleted crawl job: {}", jobId);
  }

  /**
   * Cleanup storage files for a crawl target.
   */
  private void cleanupStorageFiles(String targetUrl, String targetSlug) {
    try {
      var slug = targetSlug;
      if (isBlank(slug)) {
        slug = extractSlugFromUrl(targetUrl);
      }

      if (isNotBlank(slug)) {
        // Use deleteComicImages to delete all images for a comic
        storageService.deleteComicImages(slug);
      }
    } catch (Exception e) {
      log.warn("Failed to cleanup storage: {}", ExceptionUtils.getRootCauseMessage(e));
    }
  }

  /**
   * Get cleanup statistics.
   */
  public CleanupStats getStats() {
    var cutoffDate = now().minusDays(retentionDays);
    var pendingCleanup = crawlJobRepository.findSoftDeletedBefore(cutoffDate).size();

    return new CleanupStats(
        retentionDays,
        autoCleanupEnabled,
        cleanupStorage,
        pendingCleanup
    );
  }

}

