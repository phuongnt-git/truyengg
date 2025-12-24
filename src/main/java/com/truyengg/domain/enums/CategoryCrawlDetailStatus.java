package com.truyengg.domain.enums;

public enum CategoryCrawlDetailStatus {
  PENDING,
  RUNNING,
  COMPLETED,
  PARTIAL_FAILED,
  FAILED;

  public static CategoryCrawlDetailStatus fromString(String value) {
    if (value == null) {
      return null;
    }
    try {
      return valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown CategoryCrawlDetailStatus: " + value);
    }
  }
}

