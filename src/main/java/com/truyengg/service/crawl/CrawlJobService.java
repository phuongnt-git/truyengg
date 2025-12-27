package com.truyengg.service.crawl;

import com.truyengg.domain.entity.CrawlCheckpoint;
import com.truyengg.domain.entity.CrawlJob;
import com.truyengg.domain.entity.CrawlProgress;
import com.truyengg.domain.entity.CrawlSettings;
import com.truyengg.domain.entity.User;
import com.truyengg.domain.enums.CrawlType;
import com.truyengg.domain.enums.DownloadMode;
import com.truyengg.domain.repository.CrawlCheckpointRepository;
import com.truyengg.domain.repository.CrawlJobRepository;
import com.truyengg.domain.repository.CrawlProgressRepository;
import com.truyengg.domain.repository.CrawlQueueRepository;
import com.truyengg.domain.repository.CrawlSettingsRepository;
import com.truyengg.domain.exception.ResourceNotFoundException;
import com.truyengg.model.dto.DuplicateCheckResult;
import com.truyengg.model.event.CrawlJobCreatedEvent;
import com.truyengg.model.event.CrawlJobResumedEvent;
import com.truyengg.model.request.CrawlJobRequest;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static com.truyengg.domain.enums.CrawlStatus.CANCELLED;
import static com.truyengg.domain.enums.CrawlStatus.COMPLETED;
import static com.truyengg.domain.enums.CrawlStatus.FAILED;
import static com.truyengg.domain.enums.CrawlStatus.PAUSED;
import static com.truyengg.domain.enums.CrawlStatus.PENDING;
import static com.truyengg.domain.enums.CrawlStatus.RUNNING;
import static com.truyengg.domain.enums.DownloadMode.FULL;
import static java.time.ZonedDateTime.now;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

/**
 * Service for managing crawl jobs with unified CRUD and lifecycle operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CrawlJobService {

  CrawlJobRepository crawlJobRepository;
  CrawlSettingsRepository settingsRepository;
  CrawlProgressRepository progressRepository;
  CrawlCheckpointRepository checkpointRepository;
  CrawlQueueRepository queueRepository;
  CrawlHierarchyService hierarchyService;
  ApplicationEventPublisher eventPublisher;
  DuplicateDetectionService duplicateDetectionService;

  // ===== Create =====

  /**
   * Create a new crawl job with associated settings, progress, and checkpoint.
   */
  @Transactional
  public CrawlJob createJob(CrawlJobRequest request, User createdBy) {
    var job = CrawlJob.builder()
        .crawlType(request.crawlType())
        .targetUrl(request.targetUrl())
        .targetSlug(request.targetSlug())
        .targetName(request.targetName())
        .downloadMode(request.downloadMode())
        .createdBy(createdBy)
        .createdAt(now())
        .updatedAt(now())
        .build();

    // Handle parent job relationship
    if (request.hasParentJob()) {
      var parentJobId = request.parentJobId();
      var parentJob = crawlJobRepository.findById(parentJobId)
          .orElseThrow(() -> new ResourceNotFoundException("Parent job not found: " + parentJobId));

      job.setParentJob(parentJob);
      job.setRootJob(defaultIfNull(parentJob.getRootJob(), parentJob));
      job.setDepth(parentJob.getDepth() + 1);
    }

    // Set itemIndex if provided (>= 0)
    if (request.itemIndex() >= 0) {
      job.setItemIndex(request.itemIndex());
    }

    job = crawlJobRepository.save(job);

    // Create settings - use -1 as sentinel for "use default"
    var settings = CrawlSettings.builder()
        .crawlJob(job)
        .parallelLimit(request.parallelLimit() > 0 ? request.parallelLimit() : 3)
        .imageQuality(request.imageQuality() > 0 ? request.imageQuality() : 85)
        .timeoutSeconds(request.timeoutSeconds() > 0 ? request.timeoutSeconds() : 30)
        .skipItems(request.skipItems())
        .redownloadItems(request.redownloadItems())
        .rangeStart(request.rangeStart())
        .rangeEnd(request.rangeEnd())
        .createdAt(now())
        .updatedAt(now())
        .build();
    settingsRepository.save(settings);

    // Create progress
    var progress = CrawlProgress.builder()
        .crawlJob(job)
        .lastUpdateAt(now())
        .createdAt(now())
        .build();
    progressRepository.save(progress);

    // Create checkpoint
    var checkpoint = CrawlCheckpoint.builder()
        .crawlJob(job)
        .createdAt(now())
        .updatedAt(now())
        .build();
    checkpointRepository.save(checkpoint);

    // Create graph node if AGE is available
    hierarchyService.createGraphNode(
        job.getId(),
        job.getCrawlType().name(),
        job.getTargetUrl(),
        job.getDepth()
    );

    if (job.getParentJob() != null) {
      hierarchyService.createParentChildEdge(job.getParentJob().getId(), job.getId());
    }

    log.info("Created crawl job: {} type={} url={}", job.getId(), job.getCrawlType(), job.getTargetUrl());
    return job;
  }

  /**
   * Create a new crawl job and immediately start execution.
   * Performs duplicate detection and auto-adjusts download mode if needed.
   */
  @Transactional
  public CrawlJob createAndStartJob(CrawlJobRequest request, User createdBy) {
    // Check for duplicates before creating job
    var duplicateResult = checkDuplicateAndSuggestMode(request);

    // Auto-adjust download mode if duplicate found and mode is default (FULL)
    var adjustedRequest = request;
    if (duplicateResult.hasDuplicate() && request.downloadMode() == FULL && duplicateResult.existingChapterCount() > 0) {
      var suggestedMode = DownloadMode.UPDATE;

      adjustedRequest = new CrawlJobRequest(
          request.crawlType(),
          request.targetUrl(),
          request.targetSlug(),
          request.targetName(),
          suggestedMode,
          request.parentJobId(),
          request.itemIndex(),
          request.parallelLimit(),
          request.imageQuality(),
          request.timeoutSeconds(),
          request.skipItems(),
          request.redownloadItems(),
          request.rangeStart(),
          request.rangeEnd()
      );

      log.info("Duplicate found for URL {}, auto-set mode to {} (existing chapters: {})",
          request.targetUrl(), suggestedMode, duplicateResult.existingChapterCount());
    }

    var job = createJob(adjustedRequest, createdBy);

    // Link to existing content if duplicate found
    if (duplicateResult.hasDuplicate() && duplicateResult.existingContentId() > 0) {
      job.setContentId(duplicateResult.existingContentId());
      crawlJobRepository.save(job);
    }

    // Publish event to trigger execution
    eventPublisher.publishEvent(new CrawlJobCreatedEvent(job.getId()));
    log.info("Published CrawlJobCreatedEvent for job: {}", job.getId());

    return job;
  }

  /**
   * Check for duplicates and return result with suggested action.
   */
  public DuplicateCheckResult checkDuplicateAndSuggestMode(CrawlJobRequest request) {
    // Only check for COMIC type - other types are child jobs
    if (request.crawlType() != CrawlType.COMIC) {
      return DuplicateCheckResult.noDuplicate();
    }

    return duplicateDetectionService.checkDuplicate(request.targetUrl());
  }

  /**
   * Check if there's an active crawl for the same URL.
   */
  public Optional<CrawlJob> findActiveCrawlForUrl(String url) {
    return duplicateDetectionService.findActiveCrawlForUrl(url);
  }

  /**
   * Create a child job under a parent.
   */
  @Transactional
  public CrawlJob createChildJob(UUID parentJobId, CrawlType type, String targetUrl, String targetName, int itemIndex) {
    var parentJob = crawlJobRepository.findById(parentJobId)
        .orElseThrow(() -> new ResourceNotFoundException("Parent job not found: " + parentJobId));

    var request = CrawlJobRequest.child(type, targetUrl, targetName, parentJobId, itemIndex);
    return createJob(request, parentJob.getCreatedBy());
  }

  // ===== Read =====

  public Optional<CrawlJob> findById(UUID id) {
    return crawlJobRepository.findById(id);
  }

  public CrawlJob getById(UUID id) {
    return crawlJobRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Crawl job not found: " + id));
  }

  // Related data (settings, progress, checkpoint) use @MapsId - fetch separately via repositories

  public Page<CrawlJob> findAllRootJobs(Pageable pageable) {
    return crawlJobRepository.findAllRootJobs(pageable);
  }

  public Page<CrawlJob> findByType(CrawlType type, Pageable pageable) {
    return crawlJobRepository.findRootJobsByType(type, pageable);
  }

  public List<CrawlJob> findChildJobs(UUID parentJobId) {
    return crawlJobRepository.findByParentJobId(parentJobId);
  }

  public List<CrawlJob> findActiveJobs() {
    return crawlJobRepository.findByStatusIn(List.of(PENDING, RUNNING, PAUSED));
  }

  public List<CrawlJob> findByStatusIn(List<com.truyengg.domain.enums.CrawlStatus> statuses) {
    return crawlJobRepository.findByStatusIn(statuses);
  }

  public List<CrawlJob> findRecentJobs(int limit) {
    return crawlJobRepository.findAllRootJobs(org.springframework.data.domain.PageRequest.of(0, limit))
        .getContent();
  }

  // ===== Status transitions =====

  /**
   * Start a pending job.
   */
  @Transactional
  public CrawlJob start(UUID jobId) {
    var job = getById(jobId);

    if (job.getStatus() != PENDING) {
      throw new IllegalStateException("Can only start PENDING jobs. Current status: " + job.getStatus());
    }

    job.setStatus(RUNNING);
    job.setStartedAt(now());
    job.setUpdatedAt(now());

    // Update progress start time
    progressRepository.findById(jobId).ifPresent(progress -> {
      progress.setStartedAt(now());
      progress.setLastUpdateAt(now());
      progressRepository.save(progress);
    });

    return crawlJobRepository.save(job);
  }

  /**
   * Pause a running job.
   */
  @Transactional
  public CrawlJob pause(UUID jobId) {
    var job = getById(jobId);

    if (!job.canPause()) {
      throw new IllegalStateException("Can only pause RUNNING jobs. Current status: " + job.getStatus());
    }

    job.setStatus(PAUSED);
    job.setUpdatedAt(now());

    // Record pause in checkpoint
    checkpointRepository.recordPause(jobId, now());

    return crawlJobRepository.save(job);
  }

  /**
   * Resume a paused job.
   */
  @Transactional
  public CrawlJob resume(UUID jobId) {
    var job = getById(jobId);

    if (!job.canResume()) {
      throw new IllegalStateException("Can only resume PAUSED jobs. Current status: " + job.getStatus());
    }

    job.setStatus(RUNNING);
    job.setUpdatedAt(now());

    // Record resume in checkpoint
    checkpointRepository.recordResume(jobId, now());

    var savedJob = crawlJobRepository.save(job);

    // Get resume index from checkpoint and publish event
    var checkpoint = checkpointRepository.findById(jobId);
    var resumeFromIndex = checkpoint.map(c -> c.getLastItemIndex() + 1).orElse(0);
    eventPublisher.publishEvent(new CrawlJobResumedEvent(jobId, resumeFromIndex));

    return savedJob;
  }

  /**
   * Retry a failed job.
   */
  @Transactional
  public CrawlJob retry(UUID jobId) {
    var job = getById(jobId);

    if (!job.canRetry()) {
      throw new IllegalStateException("Can only retry FAILED jobs. Current status: " + job.getStatus());
    }

    job.setStatus(PENDING);
    job.setRetryCount(job.getRetryCount() + 1);
    job.setErrorMessage(null);
    job.setUpdatedAt(now());

    return crawlJobRepository.save(job);
  }

  /**
   * Cancel a job (can cancel PENDING, RUNNING, or PAUSED jobs).
   */
  @Transactional
  public CrawlJob cancel(UUID jobId) {
    var job = getById(jobId);

    if (job.isTerminal()) {
      throw new IllegalStateException("Cannot cancel terminal jobs. Current status: " + job.getStatus());
    }

    job.setStatus(CANCELLED);
    job.setCompletedAt(now());
    job.setUpdatedAt(now());

    // Cancel all child jobs
    var childIds = hierarchyService.findAllDescendants(jobId);
    if (!childIds.isEmpty()) {
      crawlJobRepository.updateStatusByIds(childIds, CANCELLED, now());
    }

    return crawlJobRepository.save(job);
  }

  /**
   * Mark a job as completed.
   */
  @Transactional
  public void complete(UUID jobId) {
    var job = getById(jobId);

    job.setStatus(COMPLETED);
    job.setCompletedAt(now());
    job.setUpdatedAt(now());

    crawlJobRepository.save(job);
  }

  /**
   * Mark a job as failed.
   */
  @Transactional
  public void fail(UUID jobId, String errorMessage) {
    var job = getById(jobId);

    job.setStatus(FAILED);
    job.setErrorMessage(errorMessage);
    job.setCompletedAt(now());
    job.setUpdatedAt(now());

    crawlJobRepository.save(job);
  }

  // ===== Update counters =====

  /**
   * Update job counters after processing an item.
   */
  @Transactional
  public void updateCounters(UUID jobId, int completed, int failed, int skipped, int total) {
    var job = getById(jobId);
    job.setTotalItems(total);
    job.setCompletedItems(completed);
    job.setFailedItems(failed);
    job.setSkippedItems(skipped);
    job.setUpdatedAt(now());
    crawlJobRepository.save(job);
  }

  /**
   * Link job to content after crawl completes.
   */
  @Transactional
  public void linkToContent(UUID jobId, long contentId) {
    var job = getById(jobId);
    job.setContentId(contentId);
    job.setUpdatedAt(now());
    crawlJobRepository.save(job);
  }

  // ===== Delete operations =====

  /**
   * Soft delete a job and all its descendants.
   */
  @Transactional
  public void softDelete(UUID jobId) {
    var job = crawlJobRepository.findById(jobId)
        .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));

    var now = now();

    // Get all descendants
    var descendantIds = hierarchyService.findAllDescendants(jobId);
    var allIds = Stream.concat(Stream.of(jobId), descendantIds.stream()).toList();

    // Soft delete all jobs
    crawlJobRepository.softDeleteByIds(allIds, now);

    // Soft delete related records
    settingsRepository.softDeleteByJobIds(allIds, now);
    progressRepository.softDeleteByJobIds(allIds, now);
    checkpointRepository.softDeleteByJobIds(allIds, now);
    queueRepository.softDeleteByJobIds(allIds, now);
  }

  /**
   * Hard delete a job and all its descendants.
   */
  @Transactional
  public void hardDelete(UUID jobId) {
    var job = crawlJobRepository.findByIdIncludeDeleted(jobId)
        .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));

    // Delete from graph first
    hierarchyService.deleteNodeAndDescendants(jobId);

    // Delete from relational tables (CASCADE will handle related records)
    crawlJobRepository.deleteById(jobId);
  }

  /**
   * Restore a soft-deleted job and all its descendants.
   */
  @Transactional
  public void restore(UUID jobId) {
    var job = crawlJobRepository.findByIdIncludeDeleted(jobId)
        .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));

    if (job.getDeletedAt() == null) {
      throw new IllegalStateException("Job is not deleted");
    }

    // Get all descendants
    var descendantIds = hierarchyService.findAllDescendants(jobId);
    var allIds = Stream.concat(Stream.of(jobId), descendantIds.stream()).toList();

    // Restore all jobs
    crawlJobRepository.restoreByIds(allIds);

    // Restore related records
    settingsRepository.restoreByJobIds(allIds);
    progressRepository.restoreByJobIds(allIds);
    checkpointRepository.restoreByJobIds(allIds);
    queueRepository.restoreByJobIds(allIds);
  }

  // ===== Statistics =====

  public long countActiveJobs() {
    return crawlJobRepository.countByStatusIn(List.of(PENDING, RUNNING, PAUSED));
  }

  public long countByUserAndActive(Long userId) {
    return crawlJobRepository.countByCreatedByIdAndStatusIn(userId, List.of(PENDING, RUNNING, PAUSED));
  }

  public List<Object[]> getStatsByTypeAndStatus() {
    return crawlJobRepository.countByTypeAndStatus();
  }

  // ===== Settings Operations =====

  /**
   * Update settings for a crawl job.
   */
  @Transactional
  @SuppressWarnings("unchecked")
  public CrawlJob updateSettings(UUID jobId, Map<String, Object> newSettings) {
    var job = getById(jobId);
    var settings = settingsRepository.findById(jobId)
        .orElseThrow(() -> new ResourceNotFoundException("Settings not found for job: " + jobId));

    // Update parallel limit
    if (newSettings.containsKey("parallelLimit")) {
      settings.setParallelLimit(((Number) newSettings.get("parallelLimit")).intValue());
    }

    // Update timeout
    if (newSettings.containsKey("timeoutSeconds")) {
      settings.setTimeoutSeconds(((Number) newSettings.get("timeoutSeconds")).intValue());
    }

    // Update range
    if (newSettings.containsKey("rangeStart")) {
      settings.setRangeStart(((Number) newSettings.get("rangeStart")).intValue());
    }
    if (newSettings.containsKey("rangeEnd")) {
      settings.setRangeEnd(((Number) newSettings.get("rangeEnd")).intValue());
    }

    // Update image quality
    if (newSettings.containsKey("imageQuality")) {
      settings.setImageQuality(((Number) newSettings.get("imageQuality")).intValue());
    }

    // Update skip/redownload items
    if (newSettings.containsKey("skipItems") && newSettings.get("skipItems") instanceof List) {
      settings.setSkipItems(((List<Number>) newSettings.get("skipItems")).stream()
          .map(Number::intValue)
          .toList());
    }
    if (newSettings.containsKey("redownloadItems") && newSettings.get("redownloadItems") instanceof List) {
      settings.setRedownloadItems(((List<Number>) newSettings.get("redownloadItems")).stream()
          .map(Number::intValue)
          .toList());
    }

    // Update download mode on job
    if (newSettings.containsKey("downloadMode")) {
      job.setDownloadMode(DownloadMode.valueOf((String) newSettings.get("downloadMode")));
      job.setUpdatedAt(now());
      crawlJobRepository.save(job);
    }

    settings.setUpdatedAt(now());
    settingsRepository.save(settings);

    return job;
  }
}

