package com.truyengg.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.ZonedDateTime;

@Entity
@Table(name = "chapter_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class ChapterImage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "chapter_id", nullable = false)
  Chapter chapter;

  @Column(name = "path", nullable = false, length = 500)
  String path;

  @Column(name = "original_url", length = 500)
  String originalUrl;

  @Column(name = "image_order", nullable = false)
  Integer imageOrder;

  @Column(name = "manual_order")
  Integer manualOrder;

  @Column(name = "is_downloaded", nullable = false)
  @Builder.Default
  Boolean isDownloaded = false;

  @Column(name = "is_visible", nullable = false)
  @Builder.Default
  Boolean isVisible = true;

  @Column(name = "blurhash", length = 50)
  String blurhash;

  @Column(name = "deleted_at")
  ZonedDateTime deletedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  ZonedDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at")
  ZonedDateTime updatedAt;
}

