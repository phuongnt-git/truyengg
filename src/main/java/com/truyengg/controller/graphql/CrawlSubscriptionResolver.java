package com.truyengg.controller.graphql;

import com.truyengg.domain.entity.CrawlJob;
import com.truyengg.model.graphql.CrawlEvent;
import com.truyengg.model.graphql.CrawlProgressUpdate;
import com.truyengg.model.graphql.ImageDownloadStatus;
import com.truyengg.model.graphql.MessageDto;
import com.truyengg.service.crawl.CrawlEventPublisher;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * GraphQL Subscription resolver for real-time crawl updates.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasRole('ADMIN')")
public class CrawlSubscriptionResolver {

  CrawlEventPublisher eventPublisher;

  /**
   * Subscribe to progress updates for a specific job.
   */
  @SubscriptionMapping
  public Flux<CrawlProgressUpdate> crawlProgress(@Argument UUID jobId) {
    log.debug("New subscription to crawlProgress for job: {}", jobId);
    return eventPublisher.subscribeToProgress(jobId);
  }

  /**
   * Subscribe to new messages for a specific job.
   */
  @SubscriptionMapping
  public Flux<MessageDto> crawlMessage(@Argument UUID jobId) {
    log.debug("New subscription to crawlMessage for job: {}", jobId);
    return eventPublisher.subscribeToMessages(jobId);
  }

  /**
   * Subscribe to child job creation for a parent job.
   */
  @SubscriptionMapping
  public Flux<CrawlJob> childJobCreated(@Argument UUID parentJobId) {
    log.debug("New subscription to childJobCreated for parent: {}", parentJobId);
    return eventPublisher.subscribeToChildCreated(parentJobId);
  }

  /**
   * Subscribe to image download progress for a job.
   */
  @SubscriptionMapping
  public Flux<ImageDownloadStatus> imageProgress(@Argument UUID jobId) {
    log.debug("New subscription to imageProgress for job: {}", jobId);
    return eventPublisher.subscribeToImageProgress(jobId);
  }

  /**
   * Subscribe to all crawl events (global).
   */
  @SubscriptionMapping
  public Flux<CrawlEvent> globalCrawlEvents() {
    log.debug("New subscription to globalCrawlEvents");
    return eventPublisher.subscribeToGlobalEvents();
  }

  /**
   * Subscribe to status changes for a specific job.
   */
  @SubscriptionMapping
  public Flux<CrawlJob> jobStatusChanged(@Argument UUID jobId) {
    log.debug("New subscription to jobStatusChanged for job: {}", jobId);
    return eventPublisher.subscribeToStatusChanged(jobId);
  }
}

