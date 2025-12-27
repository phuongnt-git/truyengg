package com.truyengg.domain.entity;

import com.truyengg.domain.enums.AgeRating;
import com.truyengg.domain.enums.ComicProgressStatus;
import com.truyengg.domain.enums.ComicStatus;
import com.truyengg.domain.enums.Gender;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
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

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "comics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class Comic {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false)
  String name;

  @Column(nullable = false, unique = true)
  String slug;

  @Column(name = "origin_name", columnDefinition = "TEXT")
  String originName;

  @Column(columnDefinition = "TEXT")
  String content;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(nullable = false, columnDefinition = "status_enum")
  ComicStatus status = ComicStatus.PENDING;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "progress_status", nullable = false, columnDefinition = "progress_status_enum")
  ComicProgressStatus progressStatus = ComicProgressStatus.ONGOING;

  @Column(name = "thumb_url")
  String thumbUrl;

  @Builder.Default
  Long views = 0L;

  @Column(columnDefinition = "TEXT")
  String author;

  @Column(name = "is_backed_up")
  @Builder.Default
  Boolean isBackedUp = false;

  @Column(name = "is_hot")
  @Builder.Default
  Boolean isHot = false;

  @Column(name = "backup_data", columnDefinition = "TEXT")
  String backupData;

  @Builder.Default
  Long likes = 0L;

  @Builder.Default
  Long follows = 0L;

  @Column(name = "total_chapters")
  @Builder.Default
  Integer totalChapters = 0;

  @Column(name = "last_chapter_updated_at")
  ZonedDateTime lastChapterUpdatedAt;

  @Column(length = 500, unique = true)
  String source;

  @Column(name = "cover_hash", length = 64)
  String coverHash;

  @Column(name = "cover_blurhash", length = 50)
  String coverBlurhash;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "alternative_names", columnDefinition = "TEXT[]")
  List<String> alternativeNames;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "age_rating", columnDefinition = "age_rating_enum")
  AgeRating ageRating;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(columnDefinition = "comic_gender_enum")
  Gender gender;

  @Column(length = 50)
  String country;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "merged_comic_id")
  Comic mergedComic;

  @Column(name = "updated_at")
  ZonedDateTime updatedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  ZonedDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at_local")
  ZonedDateTime updatedAtLocal;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "comic_categories",
      joinColumns = @JoinColumn(name = "comic_id"),
      inverseJoinColumns = @JoinColumn(name = "category_id")
  )
  @Builder.Default
  List<Category> categories = new ArrayList<>();

  @OneToMany(mappedBy = "comic", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  @Builder.Default
  List<Chapter> chapters = new ArrayList<>();

  @OneToMany(mappedBy = "comic", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  @Builder.Default
  List<Comment> comments = new ArrayList<>();

  @OneToMany(mappedBy = "comic", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  @Builder.Default
  List<UserFollow> userFollows = new ArrayList<>();
}

