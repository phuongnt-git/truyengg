package com.truyengg.model.graphql;

import com.truyengg.domain.enums.CrawlType;
import com.truyengg.domain.enums.DownloadMode;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

/**
 * GraphQL input type for creating a new crawl job.
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateCrawlJobInput {

  CrawlType type;
  String targetUrl;
  String targetSlug;
  String targetName;
  DownloadMode downloadMode;
  CrawlSettingsInput settings;
}

