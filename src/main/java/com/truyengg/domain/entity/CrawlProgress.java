package com.truyengg.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.time.ZonedDateTime.now;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;


/**
 * Crawl progress entity (1:1 with CrawlJob).
 * Unified counters - meaning depends on crawl_type:
 * - CATEGORY: total_items = comics, completed_items = completed comics
 * - COMIC: total_items = chapters, completed_items = completed chapters
 * - CHAPTER: total_items = images, completed_items = downloaded images
 * - IMAGE: total_items = 1, completed_items = 0 or 1
 */
@Entity
@Table(name = "crawl_progress")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@SQLRestriction("deleted_at IS NULL")
public class CrawlProgress {

  @Id
  UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "id")
  CrawlJob crawlJob;

  /**
   * Current item being processed (meaning depends on crawl_type).
   */
  @Column(name = "item_index", nullable = false)
  @Builder.Default
  int itemIndex = 0;

  @Column(name = "item_name", length = 500)
  String itemName;

  @Column(name = "item_url", columnDefinition = "TEXT")
  String itemUrl;

  /**
   * Total items to process (meaning depends on crawl_type).
   */
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

  @Column(name = "bytes_downloaded", nullable = false)
  @Builder.Default
  long bytesDownloaded = 0L;

  /**
   * Progress percentage (0-100).
   */
  @Column(nullable = false)
  @Builder.Default
  int percent = 0;

  /**
   * Current status message (merged from current_message + last_error).
   */
  @Column(name = "message", columnDefinition = "TEXT")
  String message;

  /**
   * History of all messages during this crawl progress.
   */
  @Column(name = "messages", columnDefinition = "TEXT[]")
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Builder.Default
  List<String> messages = new ArrayList<>();

  @Column(name = "started_at")
  ZonedDateTime startedAt;

  @Column(name = "last_update_at", nullable = false)
  @Builder.Default
  ZonedDateTime lastUpdateAt = now();

  @Column(name = "estimated_remaining_seconds", nullable = false)
  @Builder.Default
  int estimatedRemainingSeconds = 0;

  @Column(name = "deleted_at")
  ZonedDateTime deletedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  ZonedDateTime createdAt;

  /**
   * Calculate and update percentage.
   */
  public void updatePercent() {
    if (totalItems == 0) {
      this.percent = 0;
    } else {
      this.percent = (int) ((completedItems * 100.0) / totalItems);
    }
  }

  /**
   * Increment completed items and update percentage.
   */
  public void incrementCompleted() {
    this.completedItems++;
    updatePercent();
    this.lastUpdateAt = now();
  }

  /**
   * Increment failed items.
   */
  public void incrementFailed() {
    this.failedItems++;
    this.lastUpdateAt = now();
  }

  /**
   * Increment skipped items.
   */
  public void incrementSkipped() {
    this.skippedItems++;
    this.lastUpdateAt = now();
  }

  /**
   * Add bytes to download counter.
   */
  public void addBytesDownloaded(long bytes) {
    this.bytesDownloaded += bytes;
    this.lastUpdateAt = now();
  }

  /**
   * Update current item info.
   */
  public void updateItem(int index, String name, String url) {
    this.itemIndex = index;
    this.itemName = name;
    this.itemUrl = url;
    this.lastUpdateAt = now();
  }

  /**
   * Add a message to history and set as current message.
   */
  public void addMessage(String message) {
    this.message = message;
    if (isEmpty(messages)) {
      this.messages = new ArrayList<>();
    }
    this.messages.add(message);
    this.lastUpdateAt = now();
  }

  /**
   * Check if progress is complete.
   */
  public boolean isComplete() {
    return totalItems > 0 && (completedItems + failedItems + skippedItems) >= totalItems;
  }
}
