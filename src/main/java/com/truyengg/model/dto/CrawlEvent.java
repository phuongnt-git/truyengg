package com.truyengg.model.dto;

import com.truyengg.domain.enums.CrawlEventType;
import com.truyengg.model.request.CrawlRequest;

import java.util.UUID;

import static com.truyengg.domain.enums.CrawlEventType.RESUME;
import static com.truyengg.domain.enums.CrawlEventType.RETRY;
import static com.truyengg.domain.enums.CrawlEventType.START;

public record CrawlEvent(UUID crawlId, CrawlRequest request, CrawlEventType type) {

  public static CrawlEvent start(UUID crawlId, CrawlRequest request) {
    return new CrawlEvent(crawlId, request, START);
  }

  public static CrawlEvent resume(UUID crawlId, CrawlRequest request) {
    return new CrawlEvent(crawlId, request, RESUME);
  }

  public static CrawlEvent retry(UUID crawlId, CrawlRequest request) {
    return new CrawlEvent(crawlId, request, RETRY);
  }
}

