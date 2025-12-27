package com.truyengg.model.graphql;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

/**
 * GraphQL input type for sorting crawl jobs.
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CrawlJobSort {

  CrawlJobSortField field;
  SortDirection direction;

  public enum CrawlJobSortField {
    CREATED_AT,
    UPDATED_AT,
    STARTED_AT,
    COMPLETED_AT,
    TARGET_NAME,
    STATUS,
    TYPE,
    PERCENT,
    TOTAL_ITEMS,
    FAILED_ITEMS
  }

  public enum SortDirection {
    ASC,
    DESC
  }
}

