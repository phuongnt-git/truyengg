package com.truyengg.service.crawl;

import com.truyengg.domain.entity.CrawlCheckpoint;
import com.truyengg.domain.entity.CrawlJob;
import com.truyengg.domain.repository.CrawlCheckpointRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing crawl checkpoints for pause/resume functionality.
 * Supports checkpointing at IMAGE level for fine-grained resume.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CrawlCheckpointService {

  CrawlCheckpointRepository checkpointRepository;

  /**
   * Get checkpoint for a job.
   */
  public Optional<CrawlCheckpoint> findByJobId(UUID jobId) {
    return checkpointRepository.findById(jobId);
  }

  /**
   * Create checkpoint for a new job.
   */
  @Transactional
  public CrawlCheckpoint createCheckpoint(CrawlJob job) {
    var checkpoint = CrawlCheckpoint.builder()
        .crawlJob(job)
        .lastItemIndex(-1)
        .failedItemIndices(new ArrayList<>())
        .failedNestedItems(new HashMap<>())
        .createdAt(ZonedDateTime.now())
        .updatedAt(ZonedDateTime.now())
        .build();
    return checkpointRepository.save(checkpoint);
  }

  /**
   * Update last completed item index.
   */
  @Transactional
  public void updateLastIndex(UUID jobId, int index) {
    checkpointRepository.updateLastItemIndex(jobId, index, ZonedDateTime.now());
    log.debug("Updated checkpoint for job {}: lastItemIndex={}", jobId, index);
  }

  /**
   * Alias for updateLastIndex for clearer API.
   */
  @Transactional
  public void updateLastItemIndex(UUID jobId, int index) {
    updateLastIndex(jobId, index);
  }

  /**
   * Record that a job has been paused.
   */
  @Transactional
  public void recordPause(UUID jobId) {
    checkpointRepository.recordPause(jobId, ZonedDateTime.now());
    log.info("Recorded pause for job {}", jobId);
  }

  /**
   * Record that a job has been resumed.
   */
  @Transactional
  public void recordResume(UUID jobId) {
    checkpointRepository.recordResume(jobId, ZonedDateTime.now());
    log.info("Recorded resume for job {}", jobId);
  }

  /**
   * Add a failed item index to the checkpoint.
   */
  @Transactional
  public void addFailedIndex(UUID jobId, int index) {
    findByJobId(jobId).ifPresent(checkpoint -> {
      checkpoint.addFailedIndex(index);
      checkpoint.setUpdatedAt(ZonedDateTime.now());
      checkpointRepository.save(checkpoint);
      log.debug("Added failed index {} to checkpoint for job {}", index, jobId);
    });
  }

  /**
   * Add a nested failure (e.g., failed image within a chapter).
   */
  @Transactional
  public void addNestedFailure(UUID jobId, int parentIndex, int childIndex) {
    findByJobId(jobId).ifPresent(checkpoint -> {
      checkpoint.addNestedFailure(parentIndex, childIndex);
      checkpoint.setUpdatedAt(ZonedDateTime.now());
      checkpointRepository.save(checkpoint);
      log.debug("Added nested failure {}:{} to checkpoint for job {}",
          parentIndex, childIndex, jobId);
    });
  }

  /**
   * Save arbitrary state for complex resume scenarios.
   */
  @Transactional
  public void saveState(UUID jobId, String key, Object value) {
    findByJobId(jobId).ifPresent(checkpoint -> {
      checkpoint.saveState(key, value);
      checkpoint.setUpdatedAt(ZonedDateTime.now());
      checkpointRepository.save(checkpoint);
    });
  }

  /**
   * Get saved state from checkpoint.
   */
  public <T> T getState(UUID jobId, String key, Class<T> type) {
    return findByJobId(jobId)
        .map(c -> c.getState(key, type))
        .orElse(null);
  }

  /**
   * Get the resume index for a job.
   * Returns the index to resume from (lastItemIndex + 1, or 0 if not started).
   */
  public int getResumeIndex(UUID jobId) {
    return findByJobId(jobId)
        .map(c -> c.getLastItemIndex() + 1)
        .orElse(0);
  }

  /**
   * Get list of failed items to retry.
   */
  public List<Integer> getFailedIndices(UUID jobId) {
    return findByJobId(jobId)
        .map(CrawlCheckpoint::getFailedItemIndices)
        .orElse(List.of());
  }

  /**
   * Get nested failures map.
   */
  public Map<Integer, List<Integer>> getNestedFailures(UUID jobId) {
    return findByJobId(jobId)
        .map(CrawlCheckpoint::getFailedNestedItems)
        .orElse(Map.of());
  }

  /**
   * Clear failed indices after successful retry.
   */
  @Transactional
  public void clearFailedIndices(UUID jobId) {
    findByJobId(jobId).ifPresent(checkpoint -> {
      checkpoint.setFailedItemIndices(new ArrayList<>());
      checkpoint.setFailedNestedItems(new HashMap<>());
      checkpoint.setUpdatedAt(ZonedDateTime.now());
      checkpointRepository.save(checkpoint);
      log.info("Cleared failed indices for job {}", jobId);
    });
  }

  /**
   * Remove a specific failed index after successful retry.
   */
  @Transactional
  public void removeFailedIndex(UUID jobId, int index) {
    findByJobId(jobId).ifPresent(checkpoint -> {
      var failedIndices = checkpoint.getFailedItemIndices();
      if (failedIndices != null) {
        failedIndices.remove(Integer.valueOf(index));
        checkpoint.setUpdatedAt(ZonedDateTime.now());
        checkpointRepository.save(checkpoint);
      }
    });
  }

  /**
   * Check if job has any failed items to retry.
   */
  public boolean hasFailedItems(UUID jobId) {
    return findByJobId(jobId)
        .map(CrawlCheckpoint::hasFailedItems)
        .orElse(false);
  }

  /**
   * Get resume count for a job.
   */
  public int getResumeCount(UUID jobId) {
    return findByJobId(jobId)
        .map(CrawlCheckpoint::getResumeCount)
        .orElse(0);
  }

  /**
   * Check if job has started processing.
   */
  public boolean hasStarted(UUID jobId) {
    return findByJobId(jobId)
        .map(CrawlCheckpoint::hasStarted)
        .orElse(false);
  }
}

