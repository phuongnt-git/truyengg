package com.truyengg.domain.entity;

import com.truyengg.domain.enums.CrawlEventType;
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
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.ZonedDateTime;

@Entity
@Table(
    name = "crawl_events",
    indexes = {
        @Index(name = "idx_crawl_events_crawl_id", columnList = "crawl_id"),
        @Index(name = "idx_crawl_events_event_type", columnList = "event_type"),
        @Index(name = "idx_crawl_events_created_at", columnList = "created_at"),
        @Index(name = "idx_crawl_events_crawl_type", columnList = "crawl_id,event_type")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class CrawlEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "crawl_id", nullable = false)
  ComicCrawl crawl;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "event_type", nullable = false, columnDefinition = "crawl_event_type_enum")
  CrawlEventType eventType;

  @Column(name = "reason", columnDefinition = "TEXT")
  String reason;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  ZonedDateTime createdAt;
}

