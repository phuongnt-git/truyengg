package com.truyengg.model.graphql;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

/**
 * GraphQL input type for updating crawl settings.
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateCrawlSettingsInput {

  Integer parallelLimit;
  Integer imageQuality;
  Integer timeoutSeconds;
  String customHeaders;
}

