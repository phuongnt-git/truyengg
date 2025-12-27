package com.truyengg.model.dto;

import com.truyengg.domain.entity.SettingCategory;

import java.util.List;

/**
 * Tree node for category hierarchy display
 */
public record SettingCategoryNode(
    Integer id,
    String code,
    String name,
    String description,
    String path,
    Integer level,
    boolean isSystem,
    int settingCount,
    List<SettingCategoryNode> children
) {

  public static SettingCategoryNode from(SettingCategory category, int settingCount, List<SettingCategoryNode> children) {
    return new SettingCategoryNode(
        category.getId(),
        category.getCode(),
        category.getName(),
        category.getDescription(),
        category.getPath(),
        category.getLevel(),
        category.isSystem(),
        settingCount,
        children
    );
  }
}

