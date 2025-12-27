package com.truyengg.model.graphql;

import com.truyengg.domain.entity.CrawlJob;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Crawl event DTO for GraphQL subscriptions.
 */
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CrawlEvent {

  EventType eventType;
  UUID jobId;
  CrawlJob job;
  String message;
  ZonedDateTime timestamp;

  public enum EventType {
    JOB_CREATED,
    JOB_STARTED,
    JOB_PAUSED,
    JOB_RESUMED,
    JOB_COMPLETED,
    JOB_FAILED,
    JOB_CANCELLED,
    CHILD_CREATED,
    PROGRESS_UPDATE,
    MESSAGE_ADDED,
    IMAGE_DOWNLOADED,
    IMAGE_FAILED
  }
}

