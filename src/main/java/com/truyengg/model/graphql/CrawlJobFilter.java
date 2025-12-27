package com.truyengg.model.graphql;

import com.truyengg.domain.enums.CrawlStatus;
import com.truyengg.domain.enums.CrawlType;
import com.truyengg.domain.enums.DownloadMode;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * GraphQL input type for filtering crawl jobs.
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CrawlJobFilter {

  List<CrawlType> types;
  List<CrawlType> excludeTypes;
  List<CrawlStatus> statuses;
  List<CrawlStatus> excludeStatuses;
  List<DownloadMode> downloadModes;

  String search;
  String urlContains;
  String urlStartsWith;

  ZonedDateTime createdAfter;
  ZonedDateTime createdBefore;
  ZonedDateTime startedAfter;
  ZonedDateTime startedBefore;
  ZonedDateTime completedAfter;
  ZonedDateTime completedBefore;

  Float percentMin;
  Float percentMax;
  Integer totalItemsMin;
  Integer totalItemsMax;
  Integer failedItemsMin;
  Integer failedItemsMax;

  Boolean rootOnly;
  UUID parentJobId;
  UUID rootJobId;

  Integer depth;
  Integer depthMin;
  Integer depthMax;

  Boolean hasChildren;
  Integer retryCountMin;
  Integer retryCountMax;
}

