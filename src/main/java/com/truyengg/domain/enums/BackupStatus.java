package com.truyengg.domain.enums;

public enum BackupStatus {
  PENDING,
  PROCESSING,
  COMPLETED,
  FAILED;

  public static BackupStatus fromString(String value) {
    if (value == null) {
      return PENDING;
    }
    try {
      return valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown BackupStatus: " + value);
    }
  }
}

