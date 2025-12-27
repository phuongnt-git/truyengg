package com.truyengg.service.crawl;

import com.truyengg.domain.entity.CrawlQueue;
import com.truyengg.domain.enums.CrawlType;
import com.truyengg.domain.enums.QueueStatus;
import com.truyengg.domain.repository.CrawlQueueRepository;
import com.truyengg.model.dto.QueueStats;
import com.truyengg.model.event.CrawlJobCreatedEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.truyengg.domain.enums.CrawlType.CHAPTER;
import static com.truyengg.domain.enums.CrawlType.COMIC;
import static com.truyengg.domain.enums.CrawlType.IMAGE;
import static com.truyengg.model.request.CrawlJobRequest.child;
import static java.time.ZonedDateTime.now;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;

/**
 * Service for processing crawl queue items with type-based handling.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CrawlQueueProcessor {

  CrawlQueueRepository queueRepository;
  CrawlJobService jobService;
  CrawlProgressService progressService;
  CrawlErrorHandlingService errorHandler;
  JobScheduler jobScheduler;
  ApplicationEventPublisher eventPublisher;

  @NonFinal
  @Value("${crawl.queue.batch-size:10}")
  int batchSize;

  /**
   * Process next batch of pending queue items.
   */
  @Transactional
  public void processNextBatch() {
    var items = queueRepository.findAndLockPending(batchSize);
    processItems(items);
  }

  /**
   * Process queue items of a specific type.
   */
  @Transactional
  public int processNextBatchByType(CrawlType type) {
    var items = queueRepository.findAndLockPendingByType(type.name(), batchSize);
    return processItems(items);
  }

  /**
   * Trigger immediate using JobRunr enqueue.
   * Use when items need to be processed immediately rather than waiting for scheduled job.
   */
  public void process() {
    jobScheduler.enqueue(this::processNextBatch);
  }

  /**
   * Enqueue items and trigger processing immediately.
   */
  @Transactional
  public void enqueueAndProcess(UUID jobId, CrawlType type, List<String> urls, List<String> names) {
    enqueueItems(jobId, type, urls, names);
    process();
  }

  /**
   * Common processing logic for both batch methods.
   */
  private int processItems(List<CrawlQueue> items) {
    if (isEmpty(items)) {
      return 0;
    }

    var processed = 0;
    for (var item : items) {
      try {
        processItem(item);
        processed++;
      } catch (Exception e) {
        log.warn("Error processing queue item {}: {}", item.getId(), getRootCauseMessage(e));
        handleError(item, e);
      }
    }
    return processed;
  }

  /**
   * Process a single queue item.
   */
  public void processItem(CrawlQueue item) {
    item.markProcessing();
    queueRepository.save(item);

    switch (item.getCrawlType()) {
      case CATEGORY -> processCategoryItem(item);
      case COMIC -> processComicItem(item);
      case CHAPTER -> processChapterItem(item);
      case IMAGE -> processImageItem(item);
    }

    item.markCompleted();
    queueRepository.save(item);

    progressService.incrementCompleted(item.getCrawlJob().getId());
  }

  /**
   * Process queue item asynchronously.
   */
  @Async
  public CompletableFuture<Void> processItemAsync(CrawlQueue item) {
    try {
      processItem(item);
      return completedFuture(null);
    } catch (Exception e) {
      return failedFuture(e);
    }
  }

  /**
   * Add items to queue for a job.
   */
  @Transactional
  public void enqueueItems(
      UUID jobId,
      CrawlType type,
      List<String> urls,
      List<String> names
  ) {
    var job = jobService.getById(jobId);

    for (var i = 0; i < urls.size(); i++) {
      var item = CrawlQueue.builder()
          .crawlJob(job)
          .crawlType(type)
          .targetUrl(urls.get(i))
          .targetName(names != null && i < names.size() ? names.get(i) : null)
          .itemIndex(i)
          .createdAt(now())
          .updatedAt(now())
          .build();
      queueRepository.save(item);
    }
  }

  /**
   * Get queue statistics.
   */
  public QueueStats getStats() {
    var pending = queueRepository.countByStatus(QueueStatus.PENDING);
    var processing = queueRepository.countByStatus(QueueStatus.PROCESSING);
    var completed = queueRepository.countByStatus(QueueStatus.COMPLETED);
    var failed = queueRepository.countByStatus(QueueStatus.FAILED);
    var delayed = queueRepository.countByStatus(QueueStatus.DELAYED);

    return new QueueStats(pending, processing, completed, failed, delayed);
  }

  // ===== Private processing methods =====

  private void processCategoryItem(CrawlQueue item) {
    createChildJob(item, COMIC);
  }

  private void processComicItem(CrawlQueue item) {
    // COMIC queue item = comic URL discovered in category → create COMIC job to crawl it
    createChildJob(item, COMIC);
  }

  private void processChapterItem(CrawlQueue item) {
    createChildJob(item, CHAPTER);
  }

  /**
   * Create a child job from queue item with proper parent/root/itemIndex relationships.
   */
  private void createChildJob(CrawlQueue item, CrawlType childType) {
    var parentJob = item.getCrawlJob();
    var request = child(
        childType,
        item.getTargetUrl(),
        defaultIfBlank(item.getTargetName(), EMPTY),
        parentJob.getId(),
        item.getItemIndex()
    );

    var childJob = jobService.createJob(request, parentJob.getCreatedBy());
    eventPublisher.publishEvent(new CrawlJobCreatedEvent(childJob.getId()));
  }

  private void processImageItem(CrawlQueue item) {
    // Create IMAGE job - ImageCrawlHandler will handle the actual download
    createChildJob(item, IMAGE);
  }

  private void handleError(CrawlQueue item, Exception e) {
    var errorType = errorHandler.detectErrorType(e);
    var result = errorHandler.handleError(
        item.getCrawlJob(),
        item.getItemIndex(),
        errorType,
        getRootCauseMessage(e)
    );

    if (result.shouldRetry() && item.canRetry()) {
      if (result.isDelayed()) {
        item.scheduleRetry(result.delayMs());
      } else {
        item.resetForRetry();
      }
    } else {
      item.markFailed(getRootCauseMessage(e));
      progressService.incrementFailed(item.getCrawlJob().getId(), item.getErrorMessage());
    }

    queueRepository.save(item);
  }

}

