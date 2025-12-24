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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.ZonedDateTime;

@Entity
@Table(name = "reading_history", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "comic_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class ReadingHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "comic_id", nullable = false)
  Comic comic;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "chapter_id")
  Chapter chapter;

  @Column(length = 255)
  String slug;

  @Column(length = 255)
  String name;

  @Column(name = "thumb_url", length = 255)
  String thumbUrl;

  @Column(name = "chapter_name", nullable = false, length = 20)
  String chapterName;

  @CreatedDate
  @Column(name = "last_read_at", nullable = false, updatable = false)
  ZonedDateTime lastReadAt;
}

