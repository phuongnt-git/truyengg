package com.truyengg.domain.enums;

/**
 * Types of duplicates detected during crawling.
 */
public enum DuplicateType {
  /**
   * Same normalized URL (exact match).
   */
  EXACT_URL,

  /**
   * Same slug but different domain (mirror site).
   */
  SIMILAR_URL,

  /**
   * Same content hash (expensive comparison).
   */
  CONTENT_HASH,

  /**
   * No duplicate found.
   */
  NO_DUPLICATE
}

