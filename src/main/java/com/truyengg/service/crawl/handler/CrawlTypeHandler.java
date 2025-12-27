package com.truyengg.service.crawl.handler;

import com.truyengg.domain.entity.CrawlJob;
import com.truyengg.domain.enums.CrawlType;

/**
 * Interface for crawl type-specific handlers.
 * Each handler is responsible for processing a specific crawl type.
 */
public interface CrawlTypeHandler {

  /**
   * Handle the crawl job from the beginning.
   */
  void handle(CrawlJob job);

  /**
   * Handle the crawl job with resume from a specific index.
   *
   * @param job        The crawl job to process
   * @param startIndex The index to start from (for resume scenarios)
   */
  void handleWithResume(CrawlJob job, int startIndex);

  /**
   * Get the crawl type this handler supports.
   */
  CrawlType getSupportedType();
}

