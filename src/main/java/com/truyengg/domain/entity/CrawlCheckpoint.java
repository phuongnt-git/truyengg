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
import org.springframework.data.annotation.LastModifiedDate;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Crawl checkpoint entity (1:1 with CrawlJob).
 * Unified last_item_index - meaning depends on crawl_type:
 * - CATEGORY: last processed page index
 * - COMIC: last completed chapter index
 * - CHAPTER: last downloaded image index
 * - IMAGE: 0 or 1 (binary)
 */
@Entity
@Table(name = "crawl_checkpoints")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@SQLRestriction("deleted_at IS NULL")
public class CrawlCheckpoint {

  @Id
  UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "id")
  CrawlJob crawlJob;

  /**
   * Last completed item index. -1 means not started.
   * Meaning depends on crawl_type:
   * - CATEGORY: last page
   * - COMIC: last chapter
   * - CHAPTER: last image
   */
  @Column(name = "last_item_index", nullable = false)
  @Builder.Default
  int lastItemIndex = -1;

  /**
   * Array of failed item indices for retry.
   */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "failed_item_indices", columnDefinition = "INT[]")
  @Builder.Default
  List<Integer> failedItemIndices = new ArrayList<>();

  /**
   * Nested failures: key = parent item index, value = array of failed child indices.
   * Example: {5: [0, 3, 7]} means chapter 5 has failed images at indices 0, 3, 7.
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "failed_nested_items", columnDefinition = "JSONB")
  @Builder.Default
  Map<Integer, List<Integer>> failedNestedItems = new HashMap<>();

  @Column(name = "resume_count", nullable = false)
  @Builder.Default
  int resumeCount = 0;

  @Column(name = "paused_at")
  ZonedDateTime pausedAt;

  @Column(name = "resumed_at")
  ZonedDateTime resumedAt;

  /**
   * State snapshot for complex resume scenarios.
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "state_snapshot", columnDefinition = "JSONB")
  @Builder.Default
  Map<String, Object> stateSnapshot = new HashMap<>();

  @Column(name = "deleted_at")
  ZonedDateTime deletedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  ZonedDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  ZonedDateTime updatedAt;

  /**
   * Check if crawl has started.
   */
  public boolean hasStarted() {
    return lastItemIndex >= 0;
  }

  /**
   * Update last completed index.
   */
  public void updateLastIndex(int index) {
    this.lastItemIndex = index;
  }

  /**
   * Add a failed item index.
   */
  public void addFailedIndex(int index) {
    if (failedItemIndices == null) {
      failedItemIndices = new ArrayList<>();
    }
    if (!failedItemIndices.contains(index)) {
      failedItemIndices.add(index);
    }
  }

  /**
   * Add a nested failure.
   */
  public void addNestedFailure(int parentIndex, int childIndex) {
    if (failedNestedItems == null) {
      failedNestedItems = new HashMap<>();
    }
    failedNestedItems.computeIfAbsent(parentIndex, k -> new ArrayList<>()).add(childIndex);
  }

  /**
   * Check if there are failed items.
   */
  public boolean hasFailedItems() {
    return (failedItemIndices != null && !failedItemIndices.isEmpty())
        || (failedNestedItems != null && !failedNestedItems.isEmpty());
  }

  /**
   * Record pause time.
   */
  public void recordPause() {
    this.pausedAt = ZonedDateTime.now();
  }

  /**
   * Record resume time.
   */
  public void recordResume() {
    this.resumedAt = ZonedDateTime.now();
    this.resumeCount++;
  }

  /**
   * Store state snapshot for complex resume.
   */
  public void saveState(String key, Object value) {
    if (stateSnapshot == null) {
      stateSnapshot = new HashMap<>();
    }
    stateSnapshot.put(key, value);
  }

  /**
   * Get state from snapshot.
   */
  @SuppressWarnings("unchecked")
  public <T> T getState(String key, Class<T> type) {
    if (stateSnapshot == null) return null;
    return (T) stateSnapshot.get(key);
  }
}

