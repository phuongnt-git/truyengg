package com.truyengg.controller.graphql;

import com.truyengg.domain.entity.CrawlJob;
import com.truyengg.domain.repository.CrawlCheckpointRepository;
import com.truyengg.model.graphql.CreateCrawlJobInput;
import com.truyengg.model.graphql.UpdateCrawlSettingsInput;
import com.truyengg.model.request.CrawlJobRequest;
import com.truyengg.security.UserPrincipal;
import com.truyengg.service.auth.UserService;
import com.truyengg.service.crawl.CrawlJobService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static com.truyengg.domain.enums.DownloadMode.FULL;
import static java.time.ZonedDateTime.now;
import static java.util.Collections.emptyList;

/**
 * GraphQL Mutation resolver for crawl jobs.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasRole('ADMIN')")
public class CrawlMutationResolver {

  CrawlJobService crawlJobService;
  CrawlCheckpointRepository checkpointRepository;
  UserService userService;

  /**
   * Create a new crawl job.
   */
  @MutationMapping
  public CrawlJob createCrawlJob(
      @Argument CreateCrawlJobInput input,
      @AuthenticationPrincipal UserPrincipal principal
  ) {
    var user = userService.getUserEntityById(principal.id());

    var request = new CrawlJobRequest(
        input.getType(),
        input.getTargetUrl(),
        input.getTargetSlug(),
        input.getTargetName(),
        input.getDownloadMode() != null ? input.getDownloadMode() : FULL,
        null, // parentJobId
        -1,   // itemIndex (not applicable for root jobs)
        input.getSettings() != null && input.getSettings().getParallelLimit() != null
            ? input.getSettings().getParallelLimit() : 3,
        input.getSettings() != null && input.getSettings().getImageQuality() != null
            ? input.getSettings().getImageQuality() : 85,
        input.getSettings() != null && input.getSettings().getTimeoutSeconds() != null
            ? input.getSettings().getTimeoutSeconds() : 30,
        input.getSettings() != null ? input.getSettings().getSkipItems() : emptyList(),
        input.getSettings() != null ? input.getSettings().getRedownloadItems() : emptyList(),
        input.getSettings() != null && input.getSettings().getRangeStart() != null
            ? input.getSettings().getRangeStart() : -1,
        input.getSettings() != null && input.getSettings().getRangeEnd() != null
            ? input.getSettings().getRangeEnd() : -1
    );

    return crawlJobService.createAndStartJob(request, user);
  }

  /**
   * Start a pending crawl job.
   */
  @MutationMapping
  public CrawlJob startCrawlJob(@Argument UUID id) {
    return crawlJobService.start(id);
  }

  /**
   * Pause a running crawl job.
   */
  @MutationMapping
  public CrawlJob pauseCrawlJob(@Argument UUID id) {
    return crawlJobService.pause(id);
  }

  /**
   * Resume a paused crawl job.
   */
  @MutationMapping
  public CrawlJob resumeCrawlJob(@Argument UUID id) {
    return crawlJobService.resume(id);
  }

  /**
   * Retry a failed crawl job.
   */
  @MutationMapping
  public CrawlJob retryCrawlJob(@Argument UUID id) {
    return crawlJobService.retry(id);
  }

  /**
   * Cancel a crawl job.
   */
  @MutationMapping
  public CrawlJob cancelCrawlJob(@Argument UUID id) {
    return crawlJobService.cancel(id);
  }

  /**
   * Delete a crawl job (soft delete by default).
   */
  @MutationMapping
  public boolean deleteCrawlJob(
      @Argument UUID id,
      @Argument Boolean hard
  ) {
    try {
      if (Boolean.TRUE.equals(hard)) {
        crawlJobService.hardDelete(id);
      } else {
        crawlJobService.softDelete(id);
      }
      return true;
    } catch (Exception e) {
      log.error("Failed to delete crawl job: {}", id, e);
      return false;
    }
  }

  /**
   * Restore a soft-deleted crawl job.
   */
  @MutationMapping
  public CrawlJob restoreCrawlJob(@Argument UUID id) {
    crawlJobService.restore(id);
    return crawlJobService.getById(id);
  }

  /**
   * Update crawl job settings.
   */
  @MutationMapping
  public CrawlJob updateCrawlSettings(
      @Argument UUID id,
      @Argument UpdateCrawlSettingsInput input
  ) {
    var settings = new HashMap<String, Object>();

    if (input.getParallelLimit() != null) {
      settings.put("parallelLimit", input.getParallelLimit());
    }
    if (input.getImageQuality() != null) {
      settings.put("imageQuality", input.getImageQuality());
    }
    if (input.getTimeoutSeconds() != null) {
      settings.put("timeoutSeconds", input.getTimeoutSeconds());
    }

    return crawlJobService.updateSettings(id, settings);
  }

  /**
   * Retry specific failed images.
   */
  @MutationMapping
  public CrawlJob retryFailedImages(
      @Argument UUID jobId,
      @Argument List<Integer> indices
  ) {
    var checkpoint = checkpointRepository.findById(jobId)
        .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found for job: " + jobId));

    // Remove specified indices from failed list and add to retry queue
    var failedIndices = checkpoint.getFailedItemIndices();
    if (failedIndices != null) {
      failedIndices.removeAll(indices);
      checkpoint.setUpdatedAt(now());
      checkpointRepository.save(checkpoint);
    }

    // Trigger retry
    return crawlJobService.retry(jobId);
  }

  /**
   * Retry all failed items in a job.
   */
  @MutationMapping
  public CrawlJob retryAllFailedItems(@Argument UUID id) {
    var checkpoint = checkpointRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found for job: " + id));

    // Clear failed indices to retry all
    if (checkpoint.getFailedItemIndices() != null) {
      checkpoint.getFailedItemIndices().clear();
    }
    checkpoint.setUpdatedAt(now());
    checkpointRepository.save(checkpoint);

    return crawlJobService.retry(id);
  }

  /**
   * Skip specific failed items.
   */
  @MutationMapping
  public CrawlJob skipFailedItems(
      @Argument UUID id,
      @Argument List<Integer> indices
  ) {
    var job = crawlJobService.getById(id);
    var checkpoint = checkpointRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found for job: " + id));

    // Remove from failed list (effectively skipping)
    var failedIndices = checkpoint.getFailedItemIndices();
    if (failedIndices != null) {
      failedIndices.removeAll(indices);
    }

    // Update skipped count on job
    job.setSkippedItems(job.getSkippedItems() + indices.size());
    job.setFailedItems(Math.max(0, job.getFailedItems() - indices.size()));

    checkpoint.setUpdatedAt(now());
    checkpointRepository.save(checkpoint);

    return crawlJobService.getById(id);
  }
}

