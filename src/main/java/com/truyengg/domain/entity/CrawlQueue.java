package com.truyengg.domain.entity;

import com.truyengg.domain.enums.CrawlType;
import com.truyengg.domain.enums.QueueStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.ZonedDateTime;
import java.util.UUID;

import static com.truyengg.domain.enums.QueueStatus.COMPLETED;
import static com.truyengg.domain.enums.QueueStatus.DELAYED;
import static com.truyengg.domain.enums.QueueStatus.FAILED;
import static com.truyengg.domain.enums.QueueStatus.PENDING;
import static com.truyengg.domain.enums.QueueStatus.SKIPPED;
import static java.time.ZonedDateTime.now;

/**
 * Crawl queue entity for unified queue management.
 * Supports priority-based processing and delayed retry.
 */
@Entity
@Table(name = "crawl_queue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@SQLRestriction("deleted_at IS NULL")
public class CrawlQueue {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "crawl_job_id", nullable = false)
  CrawlJob crawlJob;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "crawl_type", nullable = false, columnDefinition = "crawl_type")
  CrawlType crawlType;

  @Column(name = "target_url", nullable = false, columnDefinition = "TEXT")
  String targetUrl;

  @Column(name = "target_name", length = 500)
  String targetName;

  /**
   * Position within parent job.
   */
  @Column(name = "item_index", nullable = false)
  @Builder.Default
  int itemIndex = 0;

  /**
   * Higher priority = processed first.
   */
  @Column(nullable = false)
  @Builder.Default
  int priority = 0;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(nullable = false, columnDefinition = "queue_status")
  @Builder.Default
  QueueStatus status = PENDING;

  @Column(name = "retry_count", nullable = false)
  @Builder.Default
  int retryCount = 0;

  @Column(name = "max_retries", nullable = false)
  @Builder.Default
  int maxRetries = 3;

  /**
   * When to retry (for DELAYED status).
   */
  @Column(name = "next_retry_at")
  ZonedDateTime nextRetryAt;

  @Column(name = "error_message", columnDefinition = "TEXT")
  String errorMessage;

  @Column(name = "started_at")
  ZonedDateTime startedAt;

  @Column(name = "completed_at")
  ZonedDateTime completedAt;

  @Column(name = "deleted_at")
  ZonedDateTime deletedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  ZonedDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  ZonedDateTime updatedAt;

  /**
   * Check if item can be retried.
   */
  public boolean canRetry() {
    return retryCount < maxRetries;
  }

  /**
   * Check if item is ready for processing.
   */
  public boolean isReady() {
    if (status == PENDING) {
      return true;
    }
    if (status == DELAYED && nextRetryAt != null) {
      return now().isAfter(nextRetryAt);
    }
    return false;
  }

  /**
   * Mark as processing.
   */
  public void markProcessing() {
    this.status = QueueStatus.PROCESSING;
    this.startedAt = now();
  }

  /**
   * Mark as completed.
   */
  public void markCompleted() {
    this.status = COMPLETED;
    this.completedAt = now();
  }

  /**
   * Mark as failed.
   */
  public void markFailed(String errorMessage) {
    this.status = FAILED;
    this.errorMessage = errorMessage;
    this.retryCount++;
  }

  /**
   * Mark as skipped.
   */
  public void markSkipped(String reason) {
    this.status = SKIPPED;
    this.errorMessage = reason;
  }

  /**
   * Schedule retry with delay.
   */
  public void scheduleRetry(long delayMs) {
    this.status = DELAYED;
    this.nextRetryAt = now().plusNanos(delayMs * 1_000_000);
    this.retryCount++;
  }

  /**
   * Reset for retry.
   */
  public void resetForRetry() {
    this.status = PENDING;
    this.errorMessage = null;
    this.nextRetryAt = null;
    this.startedAt = null;
  }
}

