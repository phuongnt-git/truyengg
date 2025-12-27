package com.truyengg.domain.entity;

import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.ZonedDateTime;
import java.util.List;

@Entity
@Table(name = "setting_categories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class SettingCategory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Integer id;

  @Column(name = "parent_id")
  Integer parentId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id", insertable = false, updatable = false)
  SettingCategory parent;

  @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
  List<SettingCategory> children;

  @Column(nullable = false, length = 50)
  String code;

  @Column(nullable = false, length = 100)
  String name;

  @Column(columnDefinition = "TEXT")
  String description;

  @Column(nullable = false)
  @Builder.Default
  int level = 0;

  @Column(nullable = false, unique = true)
  String path;

  @Type(ListArrayType.class)
  @Column(name = "path_ids", columnDefinition = "INT[]")
  List<Integer> pathIds;

  @Column(name = "is_system")
  @Builder.Default
  boolean isSystem = false;

  @Column(name = "is_active")
  @Builder.Default
  boolean isActive = true;

  @CreatedDate
  @Column(name = "created_at")
  ZonedDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at")
  ZonedDateTime updatedAt;
}

