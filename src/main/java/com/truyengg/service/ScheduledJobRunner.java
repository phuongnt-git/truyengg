package com.truyengg.service;

import com.truyengg.security.jwt.RefreshTokenService;
import com.truyengg.security.jwt.TokenBlacklistService;
import com.truyengg.service.crawl.CrawlCleanupService;
import com.truyengg.service.crawl.CrawlQueueProcessor;
import com.truyengg.service.crawl.PauseStateService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import static org.jobrunr.scheduling.cron.Cron.daily;
import static org.jobrunr.scheduling.cron.Cron.hourly;
import static org.jobrunr.scheduling.cron.Cron.minutely;

/**
 * Configures scheduled background jobs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ScheduledJobRunner implements CommandLineRunner {

  JobScheduler jobScheduler;
  RefreshTokenService refreshTokenService;
  TokenBlacklistService tokenBlacklistService;
  PauseStateService pauseStateService;
  CrawlCleanupService crawlCleanupService;
  CrawlQueueProcessor crawlQueueProcessor;

  @Override
  public void run(String... args) {
    jobScheduler.scheduleRecurrently("refresh-token-cleanup",
        daily(3, 0),
        refreshTokenService::cleanupExpiredTokens);

    jobScheduler.scheduleRecurrently("token-blacklist-cleanup",
        daily(2, 0),
        tokenBlacklistService::cleanupExpiredTokens);

    jobScheduler.scheduleRecurrently("pause-state-cleanup",
        hourly(),
        pauseStateService::cleanup);

    jobScheduler.scheduleRecurrently("crawl-cleanup",
        daily(4, 0),
        crawlCleanupService::autoCleanupOldDeletedJobs);

    jobScheduler.scheduleRecurrently("crawl-queue-processor",
        minutely(),
        crawlQueueProcessor::processNextBatch);
  }
}
