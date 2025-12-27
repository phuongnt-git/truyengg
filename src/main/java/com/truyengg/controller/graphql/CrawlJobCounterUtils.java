package com.truyengg.controller.graphql;

import com.truyengg.domain.entity.CrawlJob;
import lombok.Getter;
import lombok.experimental.UtilityClass;

import java.util.List;

/**
 * Utility class for counting CrawlJob statistics by type and status.
 */
@UtilityClass
public class CrawlJobCounterUtils {

  /**
   * Count jobs by status and type.
   */
  public static JobCounter countJobs(List<CrawlJob> jobs) {
    var counter = new JobCounter();
    for (var job : jobs) {
      counter.countStatus(job);
      counter.countType(job);
    }
    return counter;
  }

  /**
   * Counts for job status.
   */
  public record StatusCounts(
      int pending,
      int running,
      int completed,
      int failed,
      int paused,
      int cancelled
  ) {
    public static StatusCounts empty() {
      return new StatusCounts(0, 0, 0, 0, 0, 0);
    }
  }

  /**
   * Counts for job type.
   */
  public record TypeCounts(
      int category,
      int comic,
      int chapter,
      int image
  ) {
    public static TypeCounts empty() {
      return new TypeCounts(0, 0, 0, 0);
    }

    public boolean isEmpty() {
      return category == 0 && comic == 0 && chapter == 0 && image == 0;
    }
  }

  /**
   * Combined counter that tracks both status and type counts.
   */
  @Getter
  public static class JobCounter {
    private int pending, running, completed, failed, paused, cancelled;
    private int category, comic, chapter, image;

    public void countStatus(CrawlJob job) {
      switch (job.getStatus()) {
        case PENDING -> pending++;
        case RUNNING -> running++;
        case COMPLETED -> completed++;
        case FAILED -> failed++;
        case PAUSED -> paused++;
        case CANCELLED -> cancelled++;
      }
    }

    public void countType(CrawlJob job) {
      switch (job.getCrawlType()) {
        case CATEGORY -> category++;
        case COMIC -> comic++;
        case CHAPTER -> chapter++;
        case IMAGE -> image++;
      }
    }

    public StatusCounts getStatusCounts() {
      return new StatusCounts(pending, running, completed, failed, paused, cancelled);
    }

    public TypeCounts getTypeCounts() {
      return new TypeCounts(category, comic, chapter, image);
    }

  }
}

