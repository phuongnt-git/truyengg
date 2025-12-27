package com.truyengg.model.graphql;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * GraphQL input type for crawl settings.
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CrawlSettingsInput {

  Integer parallelLimit;
  Integer imageQuality;
  Integer timeoutSeconds;
  List<Integer> skipItems;
  List<Integer> redownloadItems;
  Integer rangeStart;
  Integer rangeEnd;
  String customHeaders;
}

