package com.truyengg.domain.enums;

/**
 * Crawl type discriminator for unified crawl_jobs table.
 */
public enum CrawlType {
  /**
   * Category/genre page crawl - discovers comics from category listing pages.
   */
  CATEGORY,

  /**
   * Comic crawl - downloads chapters from a comic page.
   */
  COMIC,

  /**
   * Chapter crawl - downloads images from a chapter page.
   */
  CHAPTER,

  /**
   * Image crawl - downloads a single image.
   */
  IMAGE
}

