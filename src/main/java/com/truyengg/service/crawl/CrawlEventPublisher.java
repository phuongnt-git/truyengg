package com.truyengg.service.crawl;

import com.truyengg.domain.entity.CrawlJob;
import com.truyengg.model.graphql.CrawlEvent;
import com.truyengg.model.graphql.CrawlProgressUpdate;
import com.truyengg.model.graphql.ImageDownloadStatus;
import com.truyengg.model.graphql.MessageDto;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event publisher for GraphQL subscriptions using Reactor Sinks.
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CrawlEventPublisher {

  // Global event sink for all crawl events
  Sinks.Many<CrawlEvent> globalEventSink = Sinks.many().multicast().onBackpressureBuffer();

  // Per-job sinks for targeted subscriptions
  Map<UUID, Sinks.Many<CrawlProgressUpdate>> progressSinks = new ConcurrentHashMap<>();
  Map<UUID, Sinks.Many<MessageDto>> messageSinks = new ConcurrentHashMap<>();
  Map<UUID, Sinks.Many<CrawlJob>> childCreatedSinks = new ConcurrentHashMap<>();
  Map<UUID, Sinks.Many<ImageDownloadStatus>> imageProgressSinks = new ConcurrentHashMap<>();
  Map<UUID, Sinks.Many<CrawlJob>> statusChangedSinks = new ConcurrentHashMap<>();

  // ===== Global Events =====

  public Flux<CrawlEvent> subscribeToGlobalEvents() {
    return globalEventSink.asFlux();
  }

  public void publishGlobalEvent(CrawlEvent event) {
    globalEventSink.tryEmitNext(event);
  }

  // ===== Job-specific Subscriptions =====

  public Flux<CrawlProgressUpdate> subscribeToProgress(UUID jobId) {
    return getOrCreateProgressSink(jobId).asFlux();
  }

  public Flux<MessageDto> subscribeToMessages(UUID jobId) {
    return getOrCreateMessageSink(jobId).asFlux();
  }

  public Flux<CrawlJob> subscribeToChildCreated(UUID parentJobId) {
    return getOrCreateChildCreatedSink(parentJobId).asFlux();
  }

  public Flux<ImageDownloadStatus> subscribeToImageProgress(UUID jobId) {
    return getOrCreateImageProgressSink(jobId).asFlux();
  }

  public Flux<CrawlJob> subscribeToStatusChanged(UUID jobId) {
    return getOrCreateStatusChangedSink(jobId).asFlux();
  }

  // ===== Publish Methods =====

  public void publishProgressUpdate(UUID jobId, CrawlProgressUpdate update) {
    // Publish to job-specific sink
    var sink = progressSinks.get(jobId);
    if (sink != null) {
      sink.tryEmitNext(update);
    }

    // Also publish to global
    publishGlobalEvent(CrawlEvent.builder()
        .eventType(CrawlEvent.EventType.PROGRESS_UPDATE)
        .jobId(jobId)
        .message(update.getMessage())
        .timestamp(ZonedDateTime.now())
        .build());
  }

  public void publishMessage(UUID jobId, MessageDto message) {
    var sink = messageSinks.get(jobId);
    if (sink != null) {
      sink.tryEmitNext(message);
    }

    publishGlobalEvent(CrawlEvent.builder()
        .eventType(CrawlEvent.EventType.MESSAGE_ADDED)
        .jobId(jobId)
        .message(message.getMessage())
        .timestamp(ZonedDateTime.now())
        .build());
  }

  public void publishChildCreated(UUID parentJobId, CrawlJob childJob) {
    var sink = childCreatedSinks.get(parentJobId);
    if (sink != null) {
      sink.tryEmitNext(childJob);
    }

    publishGlobalEvent(CrawlEvent.builder()
        .eventType(CrawlEvent.EventType.CHILD_CREATED)
        .jobId(childJob.getId())
        .job(childJob)
        .timestamp(ZonedDateTime.now())
        .build());
  }

  public void publishImageProgress(UUID jobId, ImageDownloadStatus status) {
    var sink = imageProgressSinks.get(jobId);
    if (sink != null) {
      sink.tryEmitNext(status);
    }

    var eventType = status.getStatus() == ImageDownloadStatus.Status.COMPLETED
        ? CrawlEvent.EventType.IMAGE_DOWNLOADED
        : CrawlEvent.EventType.IMAGE_FAILED;

    publishGlobalEvent(CrawlEvent.builder()
        .eventType(eventType)
        .jobId(jobId)
        .message("Image " + status.getIndex() + ": " + status.getStatus())
        .timestamp(ZonedDateTime.now())
        .build());
  }

  public void publishStatusChanged(CrawlJob job) {
    var sink = statusChangedSinks.get(job.getId());
    if (sink != null) {
      sink.tryEmitNext(job);
    }

    var eventType = switch (job.getStatus()) {
      case RUNNING -> CrawlEvent.EventType.JOB_STARTED;
      case PAUSED -> CrawlEvent.EventType.JOB_PAUSED;
      case COMPLETED -> CrawlEvent.EventType.JOB_COMPLETED;
      case FAILED -> CrawlEvent.EventType.JOB_FAILED;
      case CANCELLED -> CrawlEvent.EventType.JOB_CANCELLED;
      case PENDING -> CrawlEvent.EventType.JOB_CREATED;
    };

    publishGlobalEvent(CrawlEvent.builder()
        .eventType(eventType)
        .jobId(job.getId())
        .job(job)
        .timestamp(ZonedDateTime.now())
        .build());
  }

  public void publishJobCreated(CrawlJob job) {
    publishGlobalEvent(CrawlEvent.builder()
        .eventType(CrawlEvent.EventType.JOB_CREATED)
        .jobId(job.getId())
        .job(job)
        .timestamp(ZonedDateTime.now())
        .build());
  }

  // ===== Cleanup =====

  public void removeJobSinks(UUID jobId) {
    completeSink(progressSinks.remove(jobId));
    completeSink(messageSinks.remove(jobId));
    completeSink(childCreatedSinks.remove(jobId));
    completeSink(imageProgressSinks.remove(jobId));
    completeSink(statusChangedSinks.remove(jobId));
  }

  // ===== Private Helpers =====

  private Sinks.Many<CrawlProgressUpdate> getOrCreateProgressSink(UUID jobId) {
    return progressSinks.computeIfAbsent(jobId,
        k -> Sinks.many().multicast().onBackpressureBuffer());
  }

  private Sinks.Many<MessageDto> getOrCreateMessageSink(UUID jobId) {
    return messageSinks.computeIfAbsent(jobId,
        k -> Sinks.many().multicast().onBackpressureBuffer());
  }

  private Sinks.Many<CrawlJob> getOrCreateChildCreatedSink(UUID parentJobId) {
    return childCreatedSinks.computeIfAbsent(parentJobId,
        k -> Sinks.many().multicast().onBackpressureBuffer());
  }

  private Sinks.Many<ImageDownloadStatus> getOrCreateImageProgressSink(UUID jobId) {
    return imageProgressSinks.computeIfAbsent(jobId,
        k -> Sinks.many().multicast().onBackpressureBuffer());
  }

  private Sinks.Many<CrawlJob> getOrCreateStatusChangedSink(UUID jobId) {
    return statusChangedSinks.computeIfAbsent(jobId,
        k -> Sinks.many().multicast().onBackpressureBuffer());
  }

  private <T> void completeSink(Sinks.Many<T> sink) {
    if (sink != null) {
      sink.tryEmitComplete();
    }
  }
}

