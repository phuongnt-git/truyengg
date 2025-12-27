package com.truyengg.domain.entity;

import com.truyengg.domain.enums.SettingValueType;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
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
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.ZonedDateTime;
import java.util.Map;

@Entity
@Table(name = "settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class Setting {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "category_id", nullable = false)
  Integer categoryId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id", insertable = false, updatable = false)
  SettingCategory category;

  @Column(nullable = false, length = 100)
  String key;

  @Column(name = "full_key", unique = true)
  String fullKey;

  @Column(nullable = false, columnDefinition = "TEXT")
  String value;

  @Enumerated(EnumType.STRING)
  @Column(name = "value_type", nullable = false, columnDefinition = "setting_value_type")
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Builder.Default
  SettingValueType valueType = SettingValueType.STRING;

  @Column(name = "default_value", columnDefinition = "TEXT")
  String defaultValue;

  @Type(JsonBinaryType.class)
  @Column(columnDefinition = "JSONB")
  Map<String, Object> constraints;

  @Column(columnDefinition = "TEXT")
  String description;

  @Column(name = "is_required")
  @Builder.Default
  boolean isRequired = false;

  @Column(name = "is_sensitive")
  @Builder.Default
  boolean isSensitive = false;

  @Column(name = "is_readonly")
  @Builder.Default
  boolean isReadonly = false;

  @Column(name = "created_at")
  ZonedDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at")
  ZonedDateTime updatedAt;

  @Column(name = "updated_by")
  Long updatedBy;

  public Integer getMinValue() {
    if (constraints != null && constraints.containsKey("min")) {
      return ((Number) constraints.get("min")).intValue();
    }
    return null;
  }

  public Integer getMaxValue() {
    if (constraints != null && constraints.containsKey("max")) {
      return ((Number) constraints.get("max")).intValue();
    }
    return null;
  }
}

