package com.truyengg.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
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
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "crawl_progress", indexes = {
    @Index(name = "idx_crawl_progress_crawl_id", columnList = "crawl_id"),
    @Index(name = "idx_crawl_progress_last_update", columnList = "last_update")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class CrawlProgress {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "crawl_id", unique = true, nullable = false)
  ComicCrawl crawl;

  @Column(name = "current_chapter", nullable = false)
  @Builder.Default
  Integer currentChapter = 0;

  @Column(name = "total_chapters", nullable = false)
  @Builder.Default
  Integer totalChapters = 0;

  @Column(name = "downloaded_images", nullable = false)
  @Builder.Default
  Integer downloadedImages = 0;

  @Column(name = "total_images", nullable = false)
  @Builder.Default
  Integer totalImages = 0;

  @Column(name = "current_message", columnDefinition = "TEXT")
  String currentMessage;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "messages", columnDefinition = "TEXT[]")
  @Builder.Default
  List<String> messages = new ArrayList<>();

  @Column(name = "start_time", nullable = false)
  ZonedDateTime startTime;

  @Column(name = "last_update", nullable = false)
  ZonedDateTime lastUpdate;

  @Column(name = "elapsed_seconds", nullable = false)
  @Builder.Default
  Long elapsedSeconds = 0L;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  ZonedDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at")
  ZonedDateTime updatedAt;
}

