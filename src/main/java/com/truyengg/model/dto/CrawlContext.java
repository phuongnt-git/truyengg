package com.truyengg.model.dto;

import com.truyengg.domain.entity.Comic;
import com.truyengg.domain.enums.DownloadMode;
import com.truyengg.service.crawl.CrawlHandler;

import java.util.List;

public record CrawlContext(
    CrawlHandler handler,
    String domain,
    String normalizedUrl,
    Comic comic,
    DownloadMode downloadMode,
    List<Integer> downloadChapters) {
}
