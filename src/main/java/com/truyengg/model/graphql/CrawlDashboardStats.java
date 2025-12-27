package com.truyengg.model.graphql;

import com.truyengg.domain.entity.CrawlJob;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Dashboard statistics for crawl overview.
 */
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CrawlDashboardStats {

  int totalJobs;
  int activeJobs;
  int completedJobs;
  int failedJobs;
  AggregatedStats.TypeCounts byType;
  AggregatedStats.StatusCounts byStatus;
  List<CrawlJob> recentActivity;
  int queueDepth;
}

