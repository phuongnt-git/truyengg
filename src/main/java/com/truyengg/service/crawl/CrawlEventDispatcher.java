package com.truyengg.service.crawl;

import com.truyengg.model.event.CrawlJobCreatedEvent;
import com.truyengg.model.event.CrawlJobResumedEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event dispatcher for crawl job lifecycle events.
 * Uses @TransactionalEventListener to ensure events are processed AFTER transaction commits,
 * avoiding race conditions where async executor can't find the job.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CrawlEventDispatcher {

  CrawlExecutor executor;

  /**
   * Handle new crawl job creation.
   * Triggers async execution of the job AFTER the transaction commits.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCrawlJobCreated(CrawlJobCreatedEvent event) {
    log.info("Received CrawlJobCreatedEvent for job: {}", event.jobId());
    executor.execute(event.jobId());
  }

  /**
   * Handle crawl job resume.
   * Triggers async execution starting from the checkpoint AFTER the transaction commits.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCrawlJobResumed(CrawlJobResumedEvent event) {
    log.info("Received CrawlJobResumedEvent for job: {} from index: {}",
        event.jobId(), event.resumeFromIndex());
    executor.executeWithResume(event.jobId(), event.resumeFromIndex());
  }
}

