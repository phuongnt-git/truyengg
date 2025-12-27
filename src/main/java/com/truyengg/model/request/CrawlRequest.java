package com.truyengg.model.request;

import com.truyengg.domain.enums.DownloadMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.util.Collections.emptyList;
import static java.util.Optional.empty;

public record CrawlRequest(
    @NotBlank(message = "URL cannot be empty")
    String url,
    @NotNull(message = "Download mode is required")
    DownloadMode downloadMode,
    Integer partStart,
    Integer partEnd,
    List<Integer> downloadChapters,
    Optional<UUID> appendToCrawlId,
    List<Integer> skipChapters,
    List<Integer> redownloadChapters
) {

  public CrawlRequest(String url, DownloadMode downloadMode, Integer partStart, Integer partEnd,
                      List<Integer> downloadChapters) {
    this(url, downloadMode, partStart, partEnd, downloadChapters, empty(), emptyList(), emptyList());
  }

  public static CrawlRequest of(String url, DownloadMode downloadMode, Integer partStart, Integer partEnd,
                                List<Integer> downloadChapters) {
    return new CrawlRequest(url, downloadMode, partStart, partEnd, downloadChapters, empty(), emptyList(), emptyList());
  }
}
