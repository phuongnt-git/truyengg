package com.truyengg.model.request;

import com.truyengg.domain.enums.CrawlType;
import com.truyengg.domain.enums.DownloadMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

import static com.truyengg.domain.enums.DownloadMode.FULL;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

/**
 * Request for creating a new crawl job.
 */
public record CrawlJobRequest(
    @NotNull(message = "Crawl type is required")
    CrawlType crawlType,
    @NotBlank(message = "Target URL is required")
    String targetUrl,
    String targetSlug,
    String targetName,
    DownloadMode downloadMode,
    UUID parentJobId,
    int itemIndex,
    int parallelLimit,
    int imageQuality,
    int timeoutSeconds,
    List<Integer> skipItems,
    List<Integer> redownloadItems,
    int rangeStart,
    int rangeEnd
) {
  /**
   * Compact constructor to apply defaults for null values.
   */
  public CrawlJobRequest {
    targetSlug = defaultIfBlank(targetSlug, EMPTY);
    targetName = defaultIfBlank(targetName, EMPTY);
    downloadMode = defaultIfNull(downloadMode, FULL);
    skipItems = isNotEmpty(skipItems) ? skipItems : emptyList();
    redownloadItems = isNotEmpty(redownloadItems) ? redownloadItems : emptyList();
  }

  /**
   * Create a simple request with just type and URL.
   */
  public static CrawlJobRequest simple(CrawlType type, String url) {
    return new CrawlJobRequest(
        type, url, EMPTY, EMPTY,
        FULL, null, -1,
        -1, -1, -1, emptyList(), emptyList(), -1, -1
    );
  }

  /**
   * Create a child job request with itemIndex.
   */
  public static CrawlJobRequest child(CrawlType type, String url, String name, UUID parentJobId, int itemIndex) {
    return new CrawlJobRequest(
        type, url, EMPTY, name,
        FULL, parentJobId, itemIndex,
        -1, -1, -1, emptyList(), emptyList(), -1, -1
    );
  }

  /**
   * Check if parent job is specified.
   */
  public boolean hasParentJob() {
    return isNotEmpty(parentJobId);
  }
}
