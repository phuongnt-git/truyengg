package com.truyengg.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.springframework.data.annotation.LastModifiedDate;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "category_crawl_progress", indexes = {
    @Index(name = "idx_category_crawl_progress_job_id", columnList = "category_crawl_job_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CategoryCrawlProgress {

  @Id
  @Column(name = "category_crawl_job_id")
  UUID categoryCrawlJobId;

  @OneToOne
  @JoinColumn(name = "category_crawl_job_id", insertable = false, updatable = false)
  CategoryCrawlJob categoryCrawlJob;

  @Column(name = "current_page", nullable = false)
  @Builder.Default
  Integer currentPage = 0;

  @Column(name = "current_story_index", nullable = false)
  @Builder.Default
  Integer currentStoryIndex = 0;

  @Column(name = "total_stories", nullable = false)
  @Builder.Default
  Integer totalStories = 0;

  @Column(name = "crawled_stories", nullable = false)
  @Builder.Default
  Integer crawledStories = 0;

  @Column(name = "total_chapters", nullable = false)
  @Builder.Default
  Integer totalChapters = 0;

  @Column(name = "crawled_chapters", nullable = false)
  @Builder.Default
  Integer crawledChapters = 0;

  @Column(name = "total_images", nullable = false)
  @Builder.Default
  Integer totalImages = 0;

  @Column(name = "downloaded_images", nullable = false)
  @Builder.Default
  Integer downloadedImages = 0;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  ZonedDateTime updatedAt;
}

