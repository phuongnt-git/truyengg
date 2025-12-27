package com.truyengg.model.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the crawl system.
 */
@Configuration
@ConfigurationProperties(prefix = "crawl")
@Getter
@Setter
public class CrawlProperties {

  /**
   * Apache AGE configuration.
   */
  private Age age = new Age();

  /**
   * Delete/cleanup configuration.
   */
  private Delete delete = new Delete();

  /**
   * Queue configuration.
   */
  private Queue queue = new Queue();

  /**
   * Error handling configuration.
   */
  private Error error = new Error();

  /**
   * Duplicate detection configuration.
   */
  private Duplicate duplicate = new Duplicate();

  @Getter
  @Setter
  public static class Age {
    /**
     * Enable Apache AGE for graph queries.
     */
    private boolean enabled = false;
  }

  @Getter
  @Setter
  public static class Delete {
    /**
     * Days to keep soft-deleted jobs before auto hard delete.
     */
    private int retentionDays = 30;

    /**
     * Enable auto cleanup job.
     */
    private boolean autoCleanupEnabled = true;

    /**
     * Also delete storage files on hard delete.
     */
    private boolean cleanupStorage = true;

    /**
     * Cron expression for cleanup job.
     */
    private String cleanupCron = "0 0 3 * * ?";
  }

  @Getter
  @Setter
  public static class Queue {
    /**
     * Number of items to process per batch.
     */
    private int batchSize = 10;

    /**
     * Poll interval in milliseconds.
     */
    private long pollIntervalMs = 5000;
  }

  @Getter
  @Setter
  public static class Error {
    /**
     * Maximum retry attempts.
     */
    private int maxRetries = 3;

    /**
     * Base delay between retries in milliseconds.
     */
    private long retryDelayMs = 5000;
  }

  @Getter
  @Setter
  public static class Duplicate {
    /**
     * Enable content hash checking (expensive).
     */
    private boolean checkContentHash = false;
  }
}

