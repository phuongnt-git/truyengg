package com.truyengg.model.request;

import com.truyengg.domain.enums.DownloadMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CrawlRequest(
    @NotBlank(message = "URL cannot be empty")
    String url,
    @NotNull(message = "Download mode is required")
    DownloadMode downloadMode,
    Integer partStart,
    Integer partEnd,
    List<Integer> downloadChapters
) {
}
