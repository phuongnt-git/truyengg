package com.truyengg.model.response;

public record CategoryResponse(
    Long id,
    String categoryId,
    String name,
    String slug,
    String description
) {
}
