package com.truyengg.domain.enums;

public enum StoryCrawlQueueStatus {
  PENDING,
  RUNNING,
  COMPLETED;

  public static StoryCrawlQueueStatus fromString(String value) {
    if (value == null) {
      return null;
    }
    try {
      return valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown StoryCrawlQueueStatus: " + value);
    }
  }
}

