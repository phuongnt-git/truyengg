package com.truyengg.domain.entity;

import com.truyengg.domain.enums.ChapterCrawlStatus;
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
import jakarta.persistence.UniqueConstraint;
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

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@Entity
@Table(
    name = "chapter_crawl",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"crawl_id", "chapter_index"})
    },
    indexes = {
        @Index(name = "idx_chapter_crawl_id", columnList = "crawl_id"),
        @Index(name = "idx_chapter_crawl_status", columnList = "status"),
        @Index(name = "idx_chapter_crawl_id_status", columnList = "crawl_id,status"),
        @Index(name = "idx_chapter_crawl_created_at", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class ChapterCrawl {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "crawl_id", nullable = false)
  ComicCrawl crawl;

  @Column(name = "chapter_index", nullable = false)
  Integer chapterIndex;

  @Column(name = "chapter_url", nullable = false, columnDefinition = "TEXT")
  String chapterUrl;

  @Column(name = "chapter_name")
  String chapterName;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(nullable = false, columnDefinition = "chapter_crawl_status_enum")
  ChapterCrawlStatus status = ChapterCrawlStatus.PENDING;

  @Column(name = "total_images", nullable = false)
  @Builder.Default
  Integer totalImages = 0;

  @Column(name = "downloaded_images", nullable = false)
  @Builder.Default
  Integer downloadedImages = 0;

  @Column(name = "file_size_bytes")
  Long fileSizeBytes;

  @Column(name = "download_time_seconds")
  Long downloadTimeSeconds;

  @Column(name = "request_count")
  Integer requestCount;

  @Column(name = "error_count")
  Integer errorCount;

  @Column(name = "download_speed_bytes_per_second", precision = 15, scale = 2)
  BigDecimal downloadSpeedBytesPerSecond;

  @Column(name = "retry_count", nullable = false)
  @Builder.Default
  Integer retryCount = 0;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "error_messages", columnDefinition = "TEXT[]")
  List<String> errorMessages;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "image_paths", columnDefinition = "JSONB")
  List<String> imagePaths;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "original_image_paths", columnDefinition = "JSONB")
  List<String> originalImagePaths;

  @Column(name = "started_at")
  ZonedDateTime startedAt;

  @Column(name = "completed_at")
  ZonedDateTime completedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  ZonedDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  ZonedDateTime updatedAt;
}

