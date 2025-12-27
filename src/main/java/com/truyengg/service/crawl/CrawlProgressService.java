package com.truyengg.service.crawl;

import com.truyengg.domain.entity.CrawlJob;
import com.truyengg.domain.entity.CrawlProgress;
import com.truyengg.domain.repository.CrawlProgressRepository;
import com.truyengg.model.dto.CrawlProgressDto;
import com.truyengg.controller.websocket.WebSocketProgressService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static java.time.ZonedDateTime.now;

/**
 * Service for managing crawl progress and broadcasting updates via WebSocket.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CrawlProgressService {

  CrawlProgressRepository progressRepository;
  WebSocketProgressService webSocketService;

  /**
   * Get progress for a crawl job.
   */
  public Optional<CrawlProgress> findByJobId(UUID jobId) {
    return progressRepository.findById(jobId);
  }

  /**
   * Create progress for a new job.
   */
  @Transactional
  public CrawlProgress createProgress(CrawlJob job, int totalItems) {
    var progress = CrawlProgress.builder()
        .crawlJob(job)
        .totalItems(totalItems)
        .lastUpdateAt(now())
        .createdAt(now())
        .build();
    return progressRepository.save(progress);
  }

  /**
   * Update progress when starting.
   */
  @Transactional
  public void start(UUID jobId, int totalItems) {
    progressRepository.findById(jobId).ifPresent(progress -> {
      progress.setTotalItems(totalItems);
      progress.setStartedAt(now());
      progress.setLastUpdateAt(now());
      progressRepository.save(progress);
      broadcastProgress(progress);
    });
  }

  /**
   * Update current item being processed.
   */
  @Transactional
  public void updateCurrentItem(UUID jobId, int index, String name, String url) {
    progressRepository.findById(jobId).ifPresent(progress -> {
      progress.updateItem(index, name, url);
      progressRepository.save(progress);
      broadcastProgress(progress);
    });
  }

  /**
   * Increment completed items counter.
   */
  @Transactional
  public void incrementCompleted(UUID jobId) {
    progressRepository.incrementCompletedItems(jobId, now());
    progressRepository.findById(jobId).ifPresent(this::broadcastProgress);
  }

  /**
   * Increment completed items and add bytes downloaded.
   */
  @Transactional
  public void incrementCompletedWithBytes(UUID jobId, long bytes) {
    var now = now();
    progressRepository.incrementCompletedItems(jobId, now);
    progressRepository.addBytesDownloaded(jobId, bytes, now);
    progressRepository.findById(jobId).ifPresent(this::broadcastProgress);
  }

  /**
   * Increment failed items counter.
   */
  @Transactional
  public void incrementFailed(UUID jobId, String message) {
    progressRepository.findById(jobId).ifPresent(progress -> {
      progress.incrementFailed();
      if (message != null) {
        progress.addMessage("Error: " + message);
      }
      progressRepository.save(progress);
      broadcastProgress(progress);
    });
  }

  /**
   * Increment skipped items counter.
   */
  @Transactional
  public void incrementSkipped(UUID jobId) {
    progressRepository.findById(jobId).ifPresent(progress -> {
      progress.incrementSkipped();
      progressRepository.save(progress);
      broadcastProgress(progress);
    });
  }

  /**
   * Update message and add to history.
   */
  @Transactional
  public void updateMessage(UUID jobId, String message) {
    progressRepository.findById(jobId).ifPresent(progress -> {
      progress.addMessage(message);
      progressRepository.save(progress);
      broadcastProgress(progress);
    });
  }

  /**
   * Initialize progress when starting a job.
   */
  @Transactional
  public void initProgress(UUID jobId) {
    progressRepository.findById(jobId).ifPresent(progress -> {
      progress.setStartedAt(now());
      progress.setLastUpdateAt(now());
      progress.addMessage("Starting crawl...");
      progressRepository.save(progress);
      broadcastProgress(progress);
    });
  }

  /**
   * Update progress for current item.
   */
  @Transactional
  public void updateProgress(UUID jobId, int currentIndex, int totalItems, String itemName) {
    progressRepository.findById(jobId).ifPresent(progress -> {
      progress.setItemIndex(currentIndex);
      progress.setTotalItems(totalItems);
      progress.setItemName(itemName);
      progress.updatePercent();
      progress.setLastUpdateAt(now());
      progressRepository.save(progress);
      broadcastProgress(progress);
    });
  }

  /**
   * Set total items count.
   */
  @Transactional
  public void setTotalItems(UUID jobId, int totalItems) {
    progressRepository.findById(jobId).ifPresent(progress -> {
      progress.setTotalItems(totalItems);
      progress.updatePercent();
      progress.setLastUpdateAt(now());
      progressRepository.save(progress);
      broadcastProgress(progress);
    });
  }

  /**
   * Set error message.
   */
  @Transactional
  public void setError(UUID jobId, String errorMessage) {
    progressRepository.findById(jobId).ifPresent(progress -> {
      progress.addMessage("Error: " + errorMessage);
      progress.setLastUpdateAt(now());
      progressRepository.save(progress);
      broadcastProgress(progress);
    });
  }

  /**
   * Finalize progress when job completes.
   */
  @Transactional
  public void finalize(UUID jobId, String message) {
    progressRepository.findById(jobId).ifPresent(progress -> {
      progress.addMessage(message);
      progress.setPercent(100);
      progress.setLastUpdateAt(now());
      progressRepository.save(progress);
      broadcastProgress(progress);
    });
  }

  /**
   * Increment failed items without error message.
   */
  @Transactional
  public void incrementFailed(UUID jobId) {
    incrementFailed(jobId, null);
  }

  /**
   * Update total items count.
   */
  @Transactional
  public void updateTotalItems(UUID jobId, int totalItems) {
    progressRepository.findById(jobId).ifPresent(progress -> {
      progress.setTotalItems(totalItems);
      progress.updatePercent();
      progress.setLastUpdateAt(now());
      progressRepository.save(progress);
      broadcastProgress(progress);
    });
  }

  /**
   * Calculate and update estimated remaining time.
   */
  @Transactional
  public void updateEstimatedTime(UUID jobId) {
    progressRepository.findById(jobId).ifPresent(progress -> {
      int remaining = calculateRemainingSeconds(progress);
      progress.setEstimatedRemainingSeconds(remaining);
      progressRepository.save(progress);
    });
  }

  /**
   * Get progress as DTO.
   */
  public Optional<CrawlProgressDto> getProgressDto(UUID jobId) {
    return progressRepository.findById(jobId)
        .map(this::toDto);
  }

  // ===== Private methods =====

  private void broadcastProgress(CrawlProgress progress) {
    var dto = toDto(progress);
    webSocketService.sendProgress(dto);
  }

  private CrawlProgressDto toDto(CrawlProgress progress) {
    return new CrawlProgressDto(
        progress.getCrawlJob().getId(),
        progress.getItemIndex(),
        progress.getItemName(),
        progress.getItemUrl(),
        progress.getTotalItems(),
        progress.getCompletedItems(),
        progress.getFailedItems(),
        progress.getSkippedItems(),
        progress.getBytesDownloaded(),
        progress.getPercent(),
        progress.getMessage(),
        progress.getMessages(),
        progress.getEstimatedRemainingSeconds()
    );
  }

  private int calculateRemainingSeconds(CrawlProgress progress) {
    if (progress.getStartedAt() == null || progress.getCompletedItems() == 0) {
      return 0;
    }

    var elapsed = Duration.between(progress.getStartedAt(), now());
    double avgSecondsPerItem = elapsed.getSeconds() / (double) progress.getCompletedItems();
    int remaining = progress.getTotalItems() - progress.getCompletedItems() - progress.getFailedItems() - progress.getSkippedItems();

    return (int) (avgSecondsPerItem * remaining);
  }
}
