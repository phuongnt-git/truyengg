package com.truyengg.domain.entity;

import com.truyengg.domain.enums.DownloadMode;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Crawl settings entity (1:1 with CrawlJob).
 * Stores configuration for parallel processing, quality, and item selection.
 */
@Entity
@Table(name = "crawl_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@SQLRestriction("deleted_at IS NULL")
public class CrawlSettings {

  @Id
  UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "id")
  CrawlJob crawlJob;

  @Column(name = "parallel_limit", nullable = false)
  @Builder.Default
  int parallelLimit = 3;

  @Column(name = "image_quality", nullable = false)
  @Builder.Default
  int imageQuality = 85;

  @Column(name = "timeout_seconds", nullable = false)
  @Builder.Default
  int timeoutSeconds = 30;

  /**
   * Items to skip (meaning depends on crawl_type).
   * COMIC: skip chapters, CATEGORY: skip comics, etc.
   */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "skip_items", columnDefinition = "INT[]")
  List<Integer> skipItems;

  /**
   * Items to force redownload (even if they exist).
   */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "redownload_items", columnDefinition = "INT[]")
  List<Integer> redownloadItems;

  /**
   * Start from item index. -1 means no limit (start from beginning).
   */
  @Column(name = "range_start", nullable = false)
  @Builder.Default
  int rangeStart = -1;

  /**
   * End at item index. -1 means no limit (go to end).
   */
  @Column(name = "range_end", nullable = false)
  @Builder.Default
  int rangeEnd = -1;

  /**
   * Per-item settings for CATEGORY crawl type.
   * Format: [{url, mode, skipItems}, ...]
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "per_item_settings", columnDefinition = "JSONB")
  List<PerItemSetting> perItemSettings;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "custom_headers", columnDefinition = "JSONB")
  Map<String, String> customHeaders;

  @Column(name = "deleted_at")
  ZonedDateTime deletedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  ZonedDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  ZonedDateTime updatedAt;

  /**
   * Check if range is specified.
   */
  public boolean hasRange() {
    return rangeStart >= 0 || rangeEnd >= 0;
  }

  /**
   * Check if skip list is specified.
   */
  public boolean hasSkipItems() {
    return skipItems != null && !skipItems.isEmpty();
  }

  /**
   * Check if redownload list is specified.
   */
  public boolean hasRedownloadItems() {
    return redownloadItems != null && !redownloadItems.isEmpty();
  }

  /**
   * Record for per-item settings in JSONB.
   */
  public record PerItemSetting(
      String url,
      DownloadMode mode,
      List<Integer> skipItems
  ) {
  }
}

