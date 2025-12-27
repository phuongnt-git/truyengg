package com.truyengg.model.dto;

import com.truyengg.domain.entity.Setting;
import com.truyengg.domain.enums.SettingValueType;

import java.time.ZonedDateTime;

/**
 * DTO for setting list view with minimal information
 */
public record SettingListItem(
    Long id,
    String key,
    String fullKey,
    String maskedValue,
    SettingValueType valueType,
    boolean required,
    boolean sensitive,
    boolean readonly,
    String categoryPath,
    ZonedDateTime updatedAt
) {

  public static SettingListItem from(Setting setting) {
    var maskedValue = setting.isSensitive()
        ? maskValue(setting.getValue())
        : truncateValue(setting.getValue());

    return new SettingListItem(
        setting.getId(),
        setting.getKey(),
        setting.getFullKey(),
        maskedValue,
        setting.getValueType(),
        setting.isRequired(),
        setting.isSensitive(),
        setting.isReadonly(),
        setting.getCategory() != null ? setting.getCategory().getPath() : null,
        setting.getUpdatedAt()
    );
  }

  private static String maskValue(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    if (value.length() <= 4) {
      return "*".repeat(value.length());
    }
    return "*".repeat(Math.min(value.length(), 20) - 4) + value.substring(value.length() - 4);
  }

  private static String truncateValue(String value) {
    if (value == null) {
      return "";
    }
    if (value.length() > 50) {
      return value.substring(0, 47) + "...";
    }
    return value;
  }
}

