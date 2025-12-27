package com.truyengg.controller.graphql;

import com.truyengg.domain.entity.CrawlJob;
import com.truyengg.domain.enums.CrawlStatus;
import com.truyengg.model.graphql.AggregatedStats;
import com.truyengg.model.graphql.Connection;
import com.truyengg.model.graphql.CrawlDashboardStats;
import com.truyengg.model.graphql.CrawlJobFilter;
import com.truyengg.model.graphql.CrawlJobSort;
import com.truyengg.model.graphql.QueueStatus;
import com.truyengg.service.crawl.CrawlJobQueryService;
import com.truyengg.service.crawl.CrawlJobService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

import static java.util.Collections.emptyList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

/**
 * GraphQL Query resolver for crawl jobs.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasRole('ADMIN')")
public class CrawlQueryResolver {

  private static final AggregatedStats.TypeCounts EMPTY_TYPE_COUNTS = AggregatedStats.TypeCounts.builder()
      .category(0)
      .comic(0)
      .chapter(0)
      .image(0)
      .build();

  CrawlJobService crawlJobService;
  CrawlJobQueryService queryService;

  private static AggregatedStats.TypeCounts buildTypeCounts(int category, int comic, int chapter, int image) {
    if (category == 0 && comic == 0 && chapter == 0 && image == 0) {
      return EMPTY_TYPE_COUNTS;
    }
    return AggregatedStats.TypeCounts.builder()
        .category(category)
        .comic(comic)
        .chapter(chapter)
        .image(image)
        .build();
  }

  /**
   * Get a single crawl job by ID.
   */
  @QueryMapping
  public CrawlJob crawlJob(@Argument UUID id) {
    return crawlJobService.findById(id).orElse(null);
  }

  /**
   * List crawl jobs with filtering, sorting, and pagination.
   */
  @QueryMapping
  public Connection<CrawlJob> crawlJobs(
      @Argument Integer first,
      @Argument String after,
      @Argument Integer last,
      @Argument String before,
      @Argument CrawlJobFilter filter,
      @Argument List<CrawlJobSort> sort
  ) {
    return queryService.findJobsWithFilter(
        filter,
        sort,
        first,
        after,
        last,
        before
    );
  }

  /**
   * Get dashboard statistics.
   */
  @QueryMapping
  public CrawlDashboardStats crawlStats() {
    var stats = crawlJobService.getStatsByTypeAndStatus();

    var totalJobs = 0;
    var activeJobs = 0;
    var completedJobs = 0;
    var failedJobs = 0;
    var pendingJobs = 0;
    var pausedJobs = 0;
    var cancelledJobs = 0;
    var runningJobs = 0;

    var categoryCount = 0;
    var comicCount = 0;
    var chapterCount = 0;
    var imageCount = 0;

    for (var row : stats) {
      var type = row[0].toString();
      var status = row[1].toString();
      var count = ((Number) row[2]).intValue();

      totalJobs += count;

      switch (status) {
        case "PENDING" -> {
          activeJobs += count;
          pendingJobs += count;
        }
        case "RUNNING" -> {
          activeJobs += count;
          runningJobs += count;
        }
        case "PAUSED" -> {
          activeJobs += count;
          pausedJobs += count;
        }
        case "COMPLETED" -> completedJobs += count;
        case "FAILED" -> failedJobs += count;
        case "CANCELLED" -> cancelledJobs += count;
      }

      switch (type) {
        case "CATEGORY" -> categoryCount += count;
        case "COMIC" -> comicCount += count;
        case "CHAPTER" -> chapterCount += count;
        case "IMAGE" -> imageCount += count;
      }
    }

    var recentJobs = crawlJobService.findRecentJobs(10);

    return CrawlDashboardStats.builder()
        .totalJobs(totalJobs)
        .activeJobs(activeJobs)
        .completedJobs(completedJobs)
        .failedJobs(failedJobs)
        .byType(buildTypeCounts(categoryCount, comicCount, chapterCount, imageCount))
        .byStatus(AggregatedStats.StatusCounts.builder()
            .pending(pendingJobs)
            .running(runningJobs)
            .completed(completedJobs)
            .failed(failedJobs)
            .paused(pausedJobs)
            .cancelled(cancelledJobs)
            .build())
        .recentActivity(recentJobs)
        .queueDepth(activeJobs)
        .build();
  }

  /**
   * Get queue status for a specific job or global.
   */
  @QueryMapping
  public QueueStatus queueStatus(@Argument UUID jobId) {
    if (jobId != null) {
      var job = crawlJobService.findById(jobId).orElse(null);
      if (job == null) {
        return QueueStatus.empty();
      }

      var children = crawlJobService.findChildJobs(jobId);
      return buildQueueStatusFromJobs(children);
    }

    // Global queue status
    var activeJobs = crawlJobService.findByStatusIn(List.of(
        CrawlStatus.PENDING,
        CrawlStatus.RUNNING,
        CrawlStatus.PAUSED
    ));

    return buildQueueStatusFromJobs(activeJobs);
  }

  /**
   * Get search suggestions for autocomplete.
   */
  @QueryMapping
  public List<String> searchSuggestions(
      @Argument String query,
      @Argument Integer limit
  ) {
    if (query == null || query.isBlank()) {
      return emptyList();
    }

    var maxLimit = limit != null ? limit : 10;
    return queryService.findSearchSuggestions(query, maxLimit);
  }

  private QueueStatus buildQueueStatusFromJobs(List<CrawlJob> jobs) {
    if (isEmpty(jobs)) {
      return QueueStatus.empty();
    }

    var counter = CrawlJobCounterUtils.countJobs(jobs);

    return QueueStatus.builder()
        .totalItems(jobs.size())
        .pendingItems(counter.getPending())
        .processingItems(counter.getRunning())
        .failedItems(counter.getFailed())
        .itemsByType(buildTypeCounts(
            counter.getCategory(),
            counter.getComic(),
            counter.getChapter(),
            counter.getImage()
        ))
        .build();
  }
}
