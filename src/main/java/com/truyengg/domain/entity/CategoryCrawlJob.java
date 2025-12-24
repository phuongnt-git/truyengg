package com.truyengg.domain.entity;

import com.truyengg.domain.converter.CategoryCrawlJobStatusConverter;
import com.truyengg.domain.enums.CategoryCrawlJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
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
import java.util.UUID;

@Entity
@Table(name = "category_crawl_jobs", indexes = {
    @Index(name = "idx_category_crawl_jobs_status", columnList = "status"),
    @Index(name = "idx_category_crawl_jobs_created_by", columnList = "created_by"),
    @Index(name = "idx_category_crawl_jobs_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class CategoryCrawlJob {

  @Id
  @Builder.Default
  UUID id = UUID.randomUUID();

  @Column(name = "category_url", nullable = false, columnDefinition = "TEXT")
  String categoryUrl;

  @Column(nullable = false, length = 50)
  String source;

  @Convert(converter = CategoryCrawlJobStatusConverter.class)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false)
  @Builder.Default
  CategoryCrawlJobStatus status = CategoryCrawlJobStatus.PENDING;

  @Column(name = "total_pages", nullable = false)
  @Builder.Default
  Integer totalPages = 0;

  @Column(name = "crawled_pages", nullable = false)
  @Builder.Default
  Integer crawledPages = 0;

  @Column(name = "total_stories", nullable = false)
  @Builder.Default
  Integer totalStories = 0;

  @Column(name = "crawled_stories", nullable = false)
  @Builder.Default
  Integer crawledStories = 0;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by", nullable = false)
  User createdBy;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  ZonedDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  ZonedDateTime updatedAt;
}

