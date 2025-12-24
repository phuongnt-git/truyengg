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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.ZonedDateTime;
import java.util.List;

@Entity
@Table(name = "comic_crawl_checkpoints", indexes = {
    @Index(name = "idx_checkpoints_crawl_id", columnList = "crawl_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class ComicCrawlCheckpoint {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "crawl_id", unique = true, nullable = false)
  ComicCrawl crawl;

  @Column(name = "current_chapter_index", nullable = false)
  Integer currentChapterIndex;

  @Column(name = "current_image_index")
  Integer currentImageIndex;

  @Column(name = "current_image_url", columnDefinition = "TEXT")
  String currentImageUrl;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "crawled_chapters", columnDefinition = "TEXT[]")
  List<String> crawledChapters;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "chapter_progress", columnDefinition = "JSONB")
  String chapterProgress;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "image_urls", columnDefinition = "JSONB")
  List<String> imageUrls;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  ZonedDateTime createdAt;
}

