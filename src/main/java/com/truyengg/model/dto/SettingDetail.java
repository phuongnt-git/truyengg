package com.truyengg.model.dto;

import com.truyengg.domain.entity.Setting;
import com.truyengg.domain.entity.SettingCategory;
import com.truyengg.domain.enums.SettingValueType;

import java.time.ZonedDateTime;
import java.util.Map;

/**
 * DTO for setting detail view with full category information
 */
public record SettingDetail(
    Long id,
    String key,
    String fullKey,
    String value,
    String maskedValue,
    SettingValueType valueType,
    String defaultValue,
    Map<String, Object> constraints,
    String description,
    boolean required,
    boolean sensitive,
    boolean readonly,
    CategoryInfo category,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt,
    Long updatedBy
) {

  public static SettingDetail from(Setting setting) {
    var maskedValue = setting.isSensitive()
        ? maskValue(setting.getValue())
        : setting.getValue();

    return new SettingDetail(
        setting.getId(),
        setting.getKey(),
        setting.getFullKey(),
        setting.isSensitive() ? null : setting.getValue(),
        maskedValue,
        setting.getValueType(),
        setting.getDefaultValue(),
        setting.getConstraints(),
        setting.getDescription(),
        setting.isRequired(),
        setting.isSensitive(),
        setting.isReadonly(),
        CategoryInfo.from(setting.getCategory()),
        setting.getCreatedAt(),
        setting.getUpdatedAt(),
        setting.getUpdatedBy()
    );
  }

  private static String maskValue(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    if (value.length() <= 4) {
      return "*".repeat(value.length());
    }
    return "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
  }

  public record CategoryInfo(
      Integer id,
      String code,
      String name,
      String path,
      int level
  ) {
    public static CategoryInfo from(SettingCategory category) {
      if (category == null) {
        return null;
      }
      return new CategoryInfo(
          category.getId(),
          category.getCode(),
          category.getName(),
          category.getPath(),
          category.getLevel()
      );
    }
  }
}

