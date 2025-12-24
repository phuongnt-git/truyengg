package com.truyengg.service.crawl;

import com.truyengg.domain.entity.CategoryCrawlDetail;
import com.truyengg.domain.entity.CategoryCrawlJob;
import com.truyengg.domain.entity.CategoryCrawlProgress;
import com.truyengg.domain.entity.StoryCrawlQueue;
import com.truyengg.domain.enums.CategoryCrawlDetailStatus;
import com.truyengg.domain.enums.CategoryCrawlJobStatus;
import com.truyengg.domain.enums.StoryCrawlQueueStatus;
import com.truyengg.domain.repository.CategoryCrawlDetailRepository;
import com.truyengg.domain.repository.CategoryCrawlJobRepository;
import com.truyengg.domain.repository.CategoryCrawlProgressRepository;
import com.truyengg.domain.repository.StoryCrawlQueueRepository;
import com.truyengg.domain.repository.UserRepository;
import com.truyengg.exception.crawl.CrawlException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryCrawlService {

  private final CategoryCrawlJobRepository categoryCrawlJobRepository;
  private final CategoryCrawlDetailRepository categoryCrawlDetailRepository;
  private final StoryCrawlQueueRepository storyCrawlQueueRepository;
  private final CategoryCrawlProgressRepository categoryCrawlProgressRepository;
  private final CategoryPageExtractor categoryPageExtractor;
  private final UserRepository userRepository;
  private final com.truyengg.service.crawl.ComicCrawlLimitService comicCrawlLimitService;
  private final com.truyengg.service.crawl.ComicCrawlQueueService comicCrawlQueueService;

  @Transactional
  public CategoryCrawlJob startCategoryCrawl(String categoryUrl, String source, Long userId) {
    var user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

    var canStart = comicCrawlLimitService.canStartCrawl(userId);
    var jobStatus = canStart ? CategoryCrawlJobStatus.RUNNING : CategoryCrawlJobStatus.PENDING;

    var job = CategoryCrawlJob.builder()
        .categoryUrl(categoryUrl)
        .source(source)
        .status(jobStatus)
        .createdBy(user)
        .build();

    job = categoryCrawlJobRepository.save(job);

    if (canStart) {
      log.info("Created category crawl job {} with status RUNNING for user {}", job.getId(), userId);
      // Start discovery phase asynchronously
      discoverAndEnqueueStoriesAsync(job);
    } else {
      log.info("Created category crawl job {} with status PENDING for user {} (limit exceeded)", job.getId(), userId);
      // Discovery will start when job is moved to RUNNING by queue processor
    }

    return job;
  }

  @Async
  public void discoverAndEnqueueStoriesAsync(CategoryCrawlJob job) {
    // Reload job entity in new transaction
    var jobId = job.getId();
    var reloadedJob = categoryCrawlJobRepository.findById(jobId)
        .orElseThrow(() -> new IllegalArgumentException("Category crawl job not found: " + jobId));
    discoverAndEnqueueStories(reloadedJob);
  }

  @Transactional
  public void discoverAndEnqueueStories(CategoryCrawlJob job) {
    try {
      job.setStatus(CategoryCrawlJobStatus.RUNNING);
      categoryCrawlJobRepository.save(job);

      // Initialize progress
      var progress = CategoryCrawlProgress.builder()
          .categoryCrawlJobId(job.getId())
          .build();
      categoryCrawlProgressRepository.save(progress);

      log.info("Starting discovery for category crawl job: {}", job.getId());

      // Discover max page number
      var maxPage = categoryPageExtractor.getMaxPageNumber(job.getCategoryUrl());
      job.setTotalPages(maxPage);
      log.info("Found {} pages for category: {}", maxPage, job.getCategoryUrl());

      // Extract story URLs from all pages
      var allStoryUrls = new HashSet<String>();
      var baseUrl = job.getCategoryUrl();

      // Extract base URL without page number
      var baseCategoryUrl = baseUrl;
      if (baseUrl.contains("/trang-")) {
        baseCategoryUrl = baseUrl.substring(0, baseUrl.lastIndexOf("/trang-"));
      } else if (baseUrl.contains("?page=") || baseUrl.contains("&page=")) {
        var questionMarkIndex = baseUrl.indexOf("?");
        if (questionMarkIndex > 0) {
          baseCategoryUrl = baseUrl.substring(0, questionMarkIndex);
        }
      }

      for (var page = 1; page <= maxPage; page++) {
        var pageUrl = buildPageUrl(baseCategoryUrl, page);
        log.info("Extracting stories from page {}: {}", page, pageUrl);

        var storyUrls = categoryPageExtractor.extractStoryUrlsFromPage(pageUrl);
        allStoryUrls.addAll(storyUrls);

        job.setCrawledPages(page);
        categoryCrawlJobRepository.save(job);

        progress.setCurrentPage(page);
        progress.setTotalStories(allStoryUrls.size());
        categoryCrawlProgressRepository.save(progress);

        // Add delay between page requests to avoid rate limiting
        if (page < maxPage) {
          try {
            Thread.sleep(1000); // 1 second delay
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CrawlException("Discovery interrupted", e);
          }
        }
      }

      log.info("Total unique stories found: {}", allStoryUrls.size());
      job.setTotalStories(allStoryUrls.size());
      categoryCrawlJobRepository.save(job);

      // Create CategoryCrawlDetail and StoryCrawlQueue entries
      enqueueStories(new ArrayList<>(allStoryUrls), job);

      progress.setTotalStories(allStoryUrls.size());
      categoryCrawlProgressRepository.save(progress);

      log.info("Completed discovery and enqueue for category crawl job: {}", job.getId());
    } catch (Exception e) {
      log.error("Error in discovery phase for category crawl job: {}", job.getId(), e);
      job.setStatus(CategoryCrawlJobStatus.FAILED);
      categoryCrawlJobRepository.save(job);

      // Process pending jobs when category job fails
      try {
        comicCrawlQueueService.processPendingCrawls();
      } catch (Exception ex) {
        log.warn("Failed to process pending jobs after category job {} failure: {}", job.getId(), ex.getMessage());
      }

      throw new CrawlException("Failed to discover stories: " + e.getMessage(), e);
    }
  }

  @Transactional
  public void enqueueStories(List<String> storyUrls, CategoryCrawlJob categoryJob) {
    for (var storyUrl : storyUrls) {
      // Create CategoryCrawlDetail
      var detail = CategoryCrawlDetail.builder()
          .categoryCrawlJob(categoryJob)
          .storyUrl(storyUrl)
          .status(CategoryCrawlDetailStatus.PENDING)
          .build();
      detail = categoryCrawlDetailRepository.save(detail);

      // Create StoryCrawlQueue entry
      var queueItem = StoryCrawlQueue.builder()
          .categoryCrawlJob(categoryJob)
          .categoryCrawlDetail(detail)
          .storyUrl(storyUrl)
          .status(StoryCrawlQueueStatus.PENDING)
          .build();
      storyCrawlQueueRepository.save(queueItem);
    }

    log.info("Enqueued {} stories for category crawl job: {}", storyUrls.size(), categoryJob.getId());
  }

  private String buildPageUrl(String baseUrl, int page) {
    if (baseUrl.contains("/the-loai/")) {
      // Format: https://truyenqqno.com/the-loai/isekai-85/trang-6.html
      if (baseUrl.endsWith(".html")) {
        var lastSlash = baseUrl.lastIndexOf("/");
        return baseUrl.substring(0, lastSlash + 1) + "trang-" + page + ".html";
      } else {
        return baseUrl + "/trang-" + page + ".html";
      }
    } else {
      // Try query parameter format
      var separator = baseUrl.contains("?") ? "&" : "?";
      return baseUrl + separator + "page=" + page;
    }
  }
}

