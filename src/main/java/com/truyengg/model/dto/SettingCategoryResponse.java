package com.truyengg.model.dto;

import java.util.List;

/**
 * Response containing category tree with statistics
 */
public record SettingCategoryResponse(
    List<SettingCategoryNode> categories,
    long totalCategories,
    long totalSettings
) {
}

