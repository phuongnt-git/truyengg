package com.truyengg.domain.entity;

import com.truyengg.domain.enums.CrawlStatus;
import com.truyengg.domain.enums.CrawlType;
import com.truyengg.domain.enums.DownloadMode;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Unified crawl job entity supporting CATEGORY, COMIC, CHAPTER, and IMAGE crawl types.
 * Uses crawl_type discriminator for type-specific logic.
 */
@Entity
@Table(name = "crawl_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@SQLRestriction("deleted_at IS NULL")
public class CrawlJob {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  UUID id;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "crawl_type", nullable = false, columnDefinition = "crawl_type")
  CrawlType crawlType;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_job_id")
  CrawlJob parentJob;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "root_job_id")
  CrawlJob rootJob;

  @Column(nullable = false)
  @Builder.Default
  int depth = 0;

  @Column(name = "target_url", nullable = false, columnDefinition = "TEXT")
  String targetUrl;

  @Column(name = "target_slug")
  String targetSlug;

  @Column(name = "target_name", length = 500)
  String targetName;

  @Column(name = "item_index", nullable = false)
  @Builder.Default
  int itemIndex = 0;

  /**
   * Link to content table after crawl: comics.id, chapters.id, etc.
   * -1 means not linked yet.
   */
  @Column(name = "content_id", nullable = false)
  @Builder.Default
  long contentId = -1L;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(nullable = false, columnDefinition = "crawl_status")
  @Builder.Default
  CrawlStatus status = CrawlStatus.PENDING;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "download_mode", nullable = false, columnDefinition = "download_mode")
  @Builder.Default
  DownloadMode downloadMode = DownloadMode.FULL;

  @Column(name = "total_items", nullable = false)
  @Builder.Default
  int totalItems = 0;

  @Column(name = "completed_items", nullable = false)
  @Builder.Default
  int completedItems = 0;

  @Column(name = "failed_items", nullable = false)
  @Builder.Default
  int failedItems = 0;

  @Column(name = "skipped_items", nullable = false)
  @Builder.Default
  int skippedItems = 0;

  @Column(name = "retry_count", nullable = false)
  @Builder.Default
  int retryCount = 0;

  @Column(name = "error_message", columnDefinition = "TEXT")
  String errorMessage;

  @Column(name = "started_at")
  ZonedDateTime startedAt;

  @Column(name = "completed_at")
  ZonedDateTime completedAt;

  @Column(name = "deleted_at")
  ZonedDateTime deletedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by")
  User createdBy;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  ZonedDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  ZonedDateTime updatedAt;

  // 1:1 relationships removed - use repositories with @MapsId (shared PK):
  // - settingsRepository.findById(jobId)
  // - progressRepository.findById(jobId)
  // - checkpointRepository.findById(jobId)
  // Cascade delete handled by ON DELETE CASCADE in database

  @OneToMany(mappedBy = "parentJob", fetch = FetchType.LAZY)
  @Builder.Default
  List<CrawlJob> childJobs = new ArrayList<>();

  @OneToMany(mappedBy = "crawlJob", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
  @Builder.Default
  List<CrawlQueue> queueItems = new ArrayList<>();

  /**
   * Check if job is in a terminal state.
   */
  public boolean isTerminal() {
    return status == CrawlStatus.COMPLETED
        || status == CrawlStatus.FAILED
        || status == CrawlStatus.CANCELLED;
  }

  /**
   * Check if job is active (running or paused).
   */
  public boolean isActive() {
    return status == CrawlStatus.PENDING
        || status == CrawlStatus.RUNNING
        || status == CrawlStatus.PAUSED;
  }

  /**
   * Check if job can be resumed.
   */
  public boolean canResume() {
    return status == CrawlStatus.PAUSED;
  }

  /**
   * Check if job can be paused.
   */
  public boolean canPause() {
    return status == CrawlStatus.RUNNING;
  }

  /**
   * Check if job can be retried.
   */
  public boolean canRetry() {
    return status == CrawlStatus.FAILED;
  }

  /**
   * Check if content is linked.
   */
  public boolean hasContent() {
    return contentId > 0;
  }

  /**
   * Calculate progress percentage.
   */
  public int calculatePercent() {
    if (totalItems == 0) return 0;
    return (int) ((completedItems * 100.0) / totalItems);
  }

  /**
   * Increment completed items counter.
   */
  public void incrementCompleted() {
    this.completedItems++;
  }

  /**
   * Increment failed items counter.
   */
  public void incrementFailed() {
    this.failedItems++;
  }

  /**
   * Increment skipped items counter.
   */
  public void incrementSkipped() {
    this.skippedItems++;
  }
}

