package com.truyengg.domain.entity;

import com.truyengg.domain.converter.CategoryCrawlDetailStatusConverter;
import com.truyengg.domain.enums.CategoryCrawlDetailStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.ZonedDateTime;

@Entity
@Table(name = "category_crawl_details", indexes = {
    @Index(name = "idx_category_crawl_details_job_id", columnList = "category_crawl_job_id"),
    @Index(name = "idx_category_crawl_details_status", columnList = "status"),
    @Index(name = "idx_category_crawl_details_story_url", columnList = "story_url")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class CategoryCrawlDetail {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_crawl_job_id", nullable = false)
  CategoryCrawlJob categoryCrawlJob;

  @Column(name = "story_url", nullable = false, columnDefinition = "TEXT")
  String storyUrl;

  @Column(name = "story_title", length = 255)
  String storyTitle;

  @Column(name = "story_slug", length = 255)
  String storySlug;

  @Column(name = "total_chapters", nullable = false)
  @Builder.Default
  Integer totalChapters = 0;

  @Column(name = "crawled_chapters", nullable = false)
  @Builder.Default
  Integer crawledChapters = 0;

  @Column(name = "failed_chapters", nullable = false)
  @Builder.Default
  Integer failedChapters = 0;

  @Convert(converter = CategoryCrawlDetailStatusConverter.class)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false)
  @Builder.Default
  CategoryCrawlDetailStatus status = CategoryCrawlDetailStatus.PENDING;

  @Column(name = "error_message", columnDefinition = "TEXT")
  String errorMessage;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  ZonedDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  ZonedDateTime updatedAt;
}

