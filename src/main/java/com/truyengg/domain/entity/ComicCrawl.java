package com.truyengg.domain.entity;

import com.truyengg.domain.enums.ComicCrawlStatus;
import com.truyengg.domain.enums.DownloadMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "comic_crawl", indexes = {
    @Index(name = "idx_comic_crawl_created_by", columnList = "created_by"),
    @Index(name = "idx_comic_crawl_status", columnList = "status"),
    @Index(name = "idx_comic_crawl_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class ComicCrawl {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  UUID id;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(nullable = false, columnDefinition = "comic_crawl_status_enum")
  ComicCrawlStatus status = ComicCrawlStatus.RUNNING;

  @Column(nullable = false, columnDefinition = "TEXT")
  String url;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "download_mode", columnDefinition = "comic_crawl_download_mode")
  DownloadMode downloadMode = DownloadMode.FULL;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "download_chapters", columnDefinition = "jsonb")
  List<Integer> downloadChapters;

  @Column(name = "part_start")
  Integer partStart;

  @Column(name = "part_end")
  Integer partEnd;

  @Column(name = "total_chapters")
  @Builder.Default
  Integer totalChapters = 0;

  @Column(name = "downloaded_chapters")
  @Builder.Default
  Integer downloadedChapters = 0;

  @Column(name = "start_time", nullable = false)
  ZonedDateTime startTime;

  @Column(name = "end_time")
  ZonedDateTime endTime;

  @Column(name = "message", columnDefinition = "TEXT")
  String message;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by", nullable = false)
  User createdBy;

  @Column(name = "checkpoint_data", columnDefinition = "TEXT")
  String checkpointData;

  @Column(name = "deleted_at")
  ZonedDateTime deletedAt;

  @Column(name = "deleted_by")
  Long deletedBy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "deleted_by", insertable = false, updatable = false)
  User deletedByUser;

  @Column(name = "total_file_size_bytes")
  Long totalFileSizeBytes;

  @Column(name = "total_download_time_seconds")
  Long totalDownloadTimeSeconds;

  @Column(name = "total_request_count")
  Integer totalRequestCount;

  @Column(name = "total_error_count")
  Integer totalErrorCount;

  @Column(name = "average_download_speed_bytes_per_second", precision = 15, scale = 2)
  java.math.BigDecimal averageDownloadSpeedBytesPerSecond;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "chapter_urls", columnDefinition = "JSONB")
  List<String> chapterUrls;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "chapter_image_urls", columnDefinition = "JSONB")
  Map<Integer, List<String>> chapterImageUrls;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  ZonedDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at")
  ZonedDateTime updatedAt;
}

