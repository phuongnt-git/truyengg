package com.truyengg.domain.entity;

import com.truyengg.domain.converter.StoryCrawlQueueStatusConverter;
import com.truyengg.domain.enums.StoryCrawlQueueStatus;
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
@Table(name = "story_crawl_queue", indexes = {
    @Index(name = "idx_story_crawl_queue_job_id", columnList = "category_crawl_job_id"),
    @Index(name = "idx_story_crawl_queue_status", columnList = "status"),
    @Index(name = "idx_story_crawl_queue_status_created", columnList = "status, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class StoryCrawlQueue {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_crawl_job_id", nullable = false)
  CategoryCrawlJob categoryCrawlJob;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_crawl_detail_id")
  CategoryCrawlDetail categoryCrawlDetail;

  @Column(name = "story_url", nullable = false, columnDefinition = "TEXT")
  String storyUrl;

  @Convert(converter = StoryCrawlQueueStatusConverter.class)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false)
  @Builder.Default
  StoryCrawlQueueStatus status = StoryCrawlQueueStatus.PENDING;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  ZonedDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  ZonedDateTime updatedAt;
}

