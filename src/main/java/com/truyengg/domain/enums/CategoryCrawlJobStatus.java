package com.truyengg.domain.enums;

public enum CategoryCrawlJobStatus {
  PENDING,
  RUNNING,
  COMPLETED,
  FAILED,
  CANCELLED,
  PAUSED;

  public static CategoryCrawlJobStatus fromString(String value) {
    if (value == null) {
      return null;
    }
    try {
      return valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown CategoryCrawlJobStatus: " + value);
    }
  }
}

