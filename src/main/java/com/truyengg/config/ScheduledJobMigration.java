package com.truyengg.config;

import com.truyengg.service.RefreshTokenService;
import com.truyengg.service.TokenBlacklistService;
import com.truyengg.service.crawl.ComicCrawlProgressService;
import com.truyengg.service.crawl.ComicCrawlQueueService;
import com.truyengg.service.crawl.PauseStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import static org.jobrunr.scheduling.cron.Cron.daily;
import static org.jobrunr.scheduling.cron.Cron.hourly;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledJobMigration implements CommandLineRunner {

  private final JobScheduler jobScheduler;
  private final RefreshTokenService refreshTokenService;
  private final TokenBlacklistService tokenBlacklistService;
  private final ComicCrawlProgressService comicCrawlProgressService;
  private final PauseStateService pauseStateService;
  private final ComicCrawlQueueService comicCrawlQueueService;
  private final CrawlJobLimitProperties crawlJobLimitProperties;

  @Override
  public void run(String... args) {
    jobScheduler.scheduleRecurrently("refresh-token-cleanup",
        daily(3, 0),
        refreshTokenService::cleanupExpiredTokens);
    jobScheduler.scheduleRecurrently("token-blacklist-cleanup",
        daily(2, 0),
        tokenBlacklistService::cleanupExpiredTokens);
    jobScheduler.scheduleRecurrently("crawl-progress-cleanup",
        daily(),
        comicCrawlProgressService::cleanupOldProgress);
    jobScheduler.scheduleRecurrently("pause-state-cleanup",
        hourly(),
        pauseStateService::cleanup);
    jobScheduler.scheduleRecurrently("crawl-job-queue-processor",
        crawlJobLimitProperties.getQueue().getCronExpression(),
        comicCrawlQueueService::processPendingCrawls);
  }
}

