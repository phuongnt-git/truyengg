package com.truyengg.model.request;

import jakarta.validation.constraints.NotBlank;

public record CategoryCrawlRequest(
    @NotBlank(message = "Category URL không được để trống")
    String categoryUrl,

    @NotBlank(message = "Source không được để trống")
    String source
) {
}

