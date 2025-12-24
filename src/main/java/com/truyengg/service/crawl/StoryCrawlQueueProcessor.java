package com.truyengg.service.crawl;

import com.truyengg.domain.entity.CategoryCrawlJob;
import com.truyengg.domain.entity.StoryCrawlQueue;
import com.truyengg.domain.entity.User;
import com.truyengg.domain.entity.ComicCrawl;
import com.truyengg.domain.enums.CategoryCrawlDetailStatus;
import com.truyengg.domain.enums.ComicCrawlStatus;
import com.truyengg.domain.enums.DownloadMode;
import com.truyengg.domain.enums.StoryCrawlQueueStatus;
import com.truyengg.domain.enums.UserRole;
import com.truyengg.domain.repository.CategoryCrawlDetailRepository;
import com.truyengg.domain.repository.CategoryCrawlJobRepository;
import com.truyengg.domain.repository.ComicCrawlRepository;
import com.truyengg.domain.repository.StoryCrawlQueueRepository;
import com.truyengg.domain.repository.UserRepository;
import com.truyengg.model.dto.StoryCrawlResult;
import com.truyengg.model.request.CrawlRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.scheduling.cron.Cron;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

import static java.time.ZonedDateTime.now;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoryCrawlQueueProcessor implements CommandLineRunner {

  private final StoryCrawlQueueRepository storyCrawlQueueRepository;
  private final CategoryCrawlDetailRepository categoryCrawlDetailRepository;
  private final CategoryCrawlJobRepository categoryCrawlJobRepository;
  private final CategoryCrawlProgressService categoryCrawlProgressService;
  private final CategoryCrawlErrorHandler categoryCrawlErrorHandler;
  private final JobScheduler jobScheduler;
  private final com.truyengg.service.crawl.ComicCrawlQueueService comicCrawlQueueService;
  private final CrawlService crawlService;
  private final ComicCrawlRepository comicCrawlRepository;
  private final UserRepository userRepository;

  @Override
  public void run(String... args) {
    // Schedule recurring job using JobRunr
    jobScheduler.scheduleRecurrently("story-crawl-queue-processor",
        Cron.every5minutes(),
        this::processNextBatch);
    log.info("Scheduled story crawl queue processor with JobRunr");
  }

  @Transactional
  public void processNextBatch() {
    try {
      // Calculate dynamic concurrent limit based on average chapters
      var concurrentLimit = calculateConcurrentLimit();

      // Get pending stories
      var pendingStories = storyCrawlQueueRepository.findByStatusOrderByCreatedAtAsc(
          StoryCrawlQueueStatus.PENDING,
          PageRequest.of(0, concurrentLimit)
      );

      if (pendingStories.isEmpty()) {
        return;
      }

      log.info("Processing {} stories from queue", pendingStories.size());

      for (var queueItem : pendingStories) {
        try {
          processStory(queueItem);

          // Add delay between story processing to avoid server overload
          Thread.sleep(500); // 0.5 second delay
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          log.warn("Queue processing interrupted");
          break;
        } catch (Exception e) {
          log.error("Error processing story from queue: {}", queueItem.getStoryUrl(), e);
          handleStoryError(queueItem, e);
        }
      }
    } catch (Exception e) {
      log.error("Error in queue processor", e);
    }
  }

  @Transactional
  public void processStory(StoryCrawlQueue queueItem) {
    var detail = queueItem.getCategoryCrawlDetail();
    var categoryJob = queueItem.getCategoryCrawlJob();

    // Update status to RUNNING
    queueItem.setStatus(StoryCrawlQueueStatus.RUNNING);
    storyCrawlQueueRepository.save(queueItem);

    if (detail != null) {
      detail.setStatus(CategoryCrawlDetailStatus.RUNNING);
      categoryCrawlDetailRepository.save(detail);
    }

    log.info("Processing story: {} for category job: {}", queueItem.getStoryUrl(), categoryJob.getId());

    try {
      // Get or create system admin user for crawl job
      var adminUser = getOrCreateSystemAdminUser();

      // Create CrawlJob for this story
      var crawlJob = ComicCrawl.builder()
          .url(queueItem.getStoryUrl())
          .status(ComicCrawlStatus.RUNNING)
          .downloadMode(DownloadMode.FULL)
          .startTime(now())
          .createdBy(adminUser)
          .totalChapters(0)
          .downloadedChapters(0)
          .build();
      crawlJob = comicCrawlRepository.save(crawlJob);

      // Create CrawlRequest
      var crawlRequest = new CrawlRequest(
          queueItem.getStoryUrl(),
          DownloadMode.FULL,
          null, // partStart
          null, // partEnd
          null  // downloadChapters
      );

      // Start crawling synchronously (this will be async internally)
      try {
        crawlService.crawlMangaAsync(crawlRequest, crawlJob.getId());

        // Wait for job to complete (with timeout)
        var maxWaitTime = 300; // 5 minutes
        var waitInterval = 5; // 5 seconds
        var waited = 0;
        while (waited < maxWaitTime) {
          var jobOpt = comicCrawlRepository.findById(crawlJob.getId());
          if (jobOpt.isPresent()) {
            var job = jobOpt.get();
            if (job.getStatus() == ComicCrawlStatus.COMPLETED || job.getStatus() == ComicCrawlStatus.FAILED) {
              break;
            }
          }
          Thread.sleep(waitInterval * 1000);
          waited += waitInterval;
        }

        // Get final job status
        var finalJobOpt = comicCrawlRepository.findById(crawlJob.getId());
        if (finalJobOpt.isPresent()) {
          var finalJob = finalJobOpt.get();
          var crawledChapters = finalJob.getDownloadedChapters() != null ? finalJob.getDownloadedChapters() : 0;
          var totalChapters = finalJob.getTotalChapters() != null ? finalJob.getTotalChapters() : 0;
          var failedChapters = totalChapters > crawledChapters ? totalChapters - crawledChapters : 0;
          var failedUrls = new ArrayList<String>();

          var result = new StoryCrawlResult(
              finalJob.getStatus() == ComicCrawlStatus.COMPLETED,
              crawledChapters,
              failedChapters,
              0, // totalImages - can be calculated from CrawlJobDetails if needed
              failedUrls
          );

          // Update detail with results
          if (detail != null) {
            detail.setCrawledChapters(result.crawledChapters());
            detail.setFailedChapters(result.failedChapters());
            detail.setTotalChapters(totalChapters);

            if (result.failedChapters() > 0 && result.crawledChapters() > 0) {
              // Partial failure
              detail.setStatus(CategoryCrawlDetailStatus.PARTIAL_FAILED);
              categoryCrawlErrorHandler.handleChapterFailure(detail, result.failedChapterUrls(), categoryJob.getId());
            } else if (result.failedChapters() > 0 || !result.success()) {
              // Complete failure
              detail.setStatus(CategoryCrawlDetailStatus.FAILED);
              detail.setErrorMessage("Failed to crawl story");
              categoryCrawlErrorHandler.handleStoryFailure(detail, new RuntimeException("Story crawl failed"), categoryJob.getId());
            } else {
              // Success
              detail.setStatus(CategoryCrawlDetailStatus.COMPLETED);
            }
            categoryCrawlDetailRepository.save(detail);
          }

          queueItem.setStatus(StoryCrawlQueueStatus.COMPLETED);
          storyCrawlQueueRepository.save(queueItem);

          updateCategoryJobProgress(categoryJob);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Story crawl interrupted", e);
      }
    } catch (Exception e) {
      log.error("Error processing story: {}", queueItem.getStoryUrl(), e);

      if (detail != null) {
        detail.setStatus(CategoryCrawlDetailStatus.FAILED);
        detail.setErrorMessage(e.getMessage());
        categoryCrawlDetailRepository.save(detail);
        categoryCrawlErrorHandler.handleStoryFailure(detail, e, categoryJob.getId());
      }

      queueItem.setStatus(StoryCrawlQueueStatus.COMPLETED); // Mark as processed even if failed
      storyCrawlQueueRepository.save(queueItem);
      updateCategoryJobProgress(categoryJob);
    }
  }

  private int calculateConcurrentLimit() {
    var baseLimit = 5;
    var minLimit = 3;
    var maxLimit = 10;

    try {
      // Get currently running stories to calculate average chapters
      var runningStories = storyCrawlQueueRepository.findByStatusOrderByCreatedAtAsc(
          StoryCrawlQueueStatus.RUNNING, PageRequest.of(0, 10));

      if (runningStories.isEmpty()) {
        return baseLimit;
      }

      // Calculate average chapters from running stories
      var totalChapters = 0;
      var count = 0;
      for (var queueItem : runningStories) {
        if (queueItem.getCategoryCrawlDetail() != null) {
          var chapters = queueItem.getCategoryCrawlDetail().getTotalChapters();
          if (chapters > 0) {
            totalChapters += chapters;
            count++;
          }
        }
      }

      if (count == 0) {
        return baseLimit;
      }

      var avgChapters = totalChapters / count;

      // Adjust limit based on average chapters
      // Fewer chapters = can handle more stories concurrently
      // More chapters = need fewer concurrent stories
      if (avgChapters < 10) {
        return Math.min(maxLimit, baseLimit + 3);
      } else if (avgChapters < 30) {
        return baseLimit;
      } else if (avgChapters < 50) {
        return Math.max(minLimit, baseLimit - 1);
      } else {
        return Math.max(minLimit, baseLimit - 2);
      }
    } catch (Exception e) {
      log.warn("Error calculating concurrent limit, using base limit: {}", baseLimit, e);
      return baseLimit;
    }
  }

  private void handleStoryError(StoryCrawlQueue queueItem, Exception error) {
    var detail = queueItem.getCategoryCrawlDetail();
    var categoryJob = queueItem.getCategoryCrawlJob();

    queueItem.setStatus(StoryCrawlQueueStatus.COMPLETED); // Mark as processed
    storyCrawlQueueRepository.save(queueItem);

    if (detail != null) {
      detail.setStatus(CategoryCrawlDetailStatus.FAILED);
      detail.setErrorMessage(error.getMessage());
      categoryCrawlDetailRepository.save(detail);

      // Create failed CrawlJob for retry
      categoryCrawlErrorHandler.handleStoryFailure(detail, error, categoryJob.getId());
    }

    updateCategoryJobProgress(categoryJob);
  }

  private void updateCategoryJobProgress(CategoryCrawlJob categoryJob) {
    var completedCount = categoryCrawlDetailRepository.countByStatusAndCategoryCrawlJob_Id(
        CategoryCrawlDetailStatus.COMPLETED, categoryJob.getId());
    var partialFailedCount = categoryCrawlDetailRepository.countByStatusAndCategoryCrawlJob_Id(
        CategoryCrawlDetailStatus.PARTIAL_FAILED, categoryJob.getId());
    var runningCount = categoryCrawlDetailRepository.countByStatusAndCategoryCrawlJob_Id(
        CategoryCrawlDetailStatus.RUNNING, categoryJob.getId());

    var crawledStories = (int) (completedCount + partialFailedCount);
    categoryJob.setCrawledStories(crawledStories);
    categoryCrawlJobRepository.save(categoryJob);

    // Calculate totals from all details
    var allDetails = categoryCrawlDetailRepository.findByCategoryCrawlJob_Id(categoryJob.getId());
    var totalChapters = allDetails.stream()
        .mapToInt(d -> d.getTotalChapters() != null ? d.getTotalChapters() : 0)
        .sum();
    var crawledChapters = allDetails.stream()
        .mapToInt(d -> d.getCrawledChapters() != null ? d.getCrawledChapters() : 0)
        .sum();
    var totalImages = 0; // Will be updated as images are processed
    var downloadedImages = 0; // Will be updated as images are processed

    // Update progress
    categoryCrawlProgressService.updateProgress(
        categoryJob.getId(),
        categoryJob.getCrawledPages() != null ? categoryJob.getCrawledPages() : 0,
        crawledStories,
        categoryJob.getTotalStories() != null ? categoryJob.getTotalStories() : 0,
        crawledStories,
        totalChapters,
        crawledChapters,
        totalImages,
        downloadedImages
    );

    // Check if job is completed
    if (crawledStories >= categoryJob.getTotalStories() && runningCount == 0) {
      categoryJob.setStatus(com.truyengg.domain.enums.CategoryCrawlJobStatus.COMPLETED);
      categoryCrawlJobRepository.save(categoryJob);

      // Process pending crawls when category crawl completes
      try {
        comicCrawlQueueService.processPendingCrawls();
      } catch (Exception e) {
        log.warn("Failed to process pending jobs after category job {} completion: {}", categoryJob.getId(), e.getMessage());
      }
    }
  }

  private User getOrCreateSystemAdminUser() {
    // Try to find an admin user
    var adminUsers = userRepository.findAll().stream()
        .filter(user -> user.getRoles() == UserRole.ADMIN)
        .toList();

    if (!adminUsers.isEmpty()) {
      return adminUsers.get(0);
    }

    // If no admin exists, create a system user (this should rarely happen)
    log.warn("No admin user found, creating system user for category crawl");
    var systemUser = User.builder()
        .email("system@truyengg.com")
        .username("system")
        .roles(UserRole.ADMIN)
        .password("$2a$10$dummy") // Dummy password hash - should not be used for login
        .build();
    return userRepository.save(systemUser);
  }
}

