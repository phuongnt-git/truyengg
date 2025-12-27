package com.truyengg.model.graphql;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

/**
 * Status of individual image download within a chapter job.
 */
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ImageDownloadStatus {

  int index;
  String originalUrl;
  String path;
  String blurhash;
  Status status;
  Long size;
  String error;

  public enum Status {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    SKIPPED
  }
}

