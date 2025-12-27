package com.truyengg.domain.enums;

/**
 * Download mode determines what to crawl.
 */
public enum DownloadMode {
  /**
   * Download all items (re-crawl) - default for new comics.
   */
  FULL,

  /**
   * Only new items (compared to DB) - for existing comics.
   */
  UPDATE,

  /**
   * User-selected range/specific items.
   */
  PARTIAL,

  /**
   * Skip all - for duplicates that user chose to skip.
   */
  NONE
}

