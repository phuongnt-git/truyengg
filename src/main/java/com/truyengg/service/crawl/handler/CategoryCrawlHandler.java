package com.truyengg.service.crawl.handler;

import com.truyengg.domain.entity.CrawlJob;
import com.truyengg.domain.enums.CrawlType;
import com.truyengg.domain.repository.CrawlSettingsRepository;
import com.truyengg.service.crawl.CrawlCheckpointService;
import com.truyengg.service.crawl.CrawlHttpClient;
import com.truyengg.service.crawl.CrawlJobService;
import com.truyengg.service.crawl.CrawlProgressService;
import com.truyengg.service.crawl.CrawlQueueProcessor;
import com.truyengg.service.crawl.DownloadModeService;
import com.truyengg.service.crawl.PauseStateService;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.truyengg.domain.constant.AppConstants.COMIC_URL_PATTERN;
import static com.truyengg.domain.enums.CrawlSourceType.detectSourceFromUrl;
import static com.truyengg.domain.enums.CrawlType.CATEGORY;
import static com.truyengg.domain.enums.CrawlType.COMIC;
import static com.truyengg.service.crawl.SlugExtractor.extractSlugFromUrl;
import static java.lang.Math.max;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;
import static org.jsoup.Jsoup.parse;

/**
 * Handler for CATEGORY type crawl jobs.
 * Discovers comics from a category page and creates child COMIC jobs.
 */
@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CategoryCrawlHandler extends AbstractCrawlHandler {

  public CategoryCrawlHandler(
      CrawlJobService jobService,
      CrawlProgressService progressService,
      CrawlCheckpointService checkpointService,
      CrawlHttpClient httpClient,
      CrawlHandlerFactory handlerFactory,
      PauseStateService pauseStateService,
      DownloadModeService downloadModeService,
      CrawlSettingsRepository settingsRepository,
      CrawlQueueProcessor queueProcessor) {
    super(jobService, progressService, checkpointService, httpClient, handlerFactory, pauseStateService, downloadModeService, settingsRepository, queueProcessor);
  }

  @Override
  public void handleWithResume(CrawlJob job, int startIndex) {
    var jobId = job.getId();

    updateMessage(jobId, "Extracting comic URLs from category page...");

    var sourceType = detectSourceFromUrl(job.getTargetUrl());
    var handler = handlerFactory.getHandler(sourceType);
    var domain = handler.extractDomainFromUrl(job.getTargetUrl());

    // Fetch category page and extract comic URLs
    var headers = httpClient.buildHeaders(domain);
    var htmlContent = httpClient.fetchUrl(job.getTargetUrl(), headers, false);

    if (isBlank(htmlContent)) {
      throw new IllegalStateException("Failed to fetch category page: " + job.getTargetUrl());
    }

    var doc = parse(htmlContent);
    var comicUrls = extractComicUrls(doc);

    if (comicUrls.isEmpty()) {
      updateMessage(jobId, "No comics found in category page");
      return;
    }

    // Apply range settings
    var settings = getSettings(jobId);
    var effectiveStart = max(startIndex, getEffectiveStart(settings));
    var effectiveEnd = getEffectiveEnd(settings, comicUrls.size());

    setTotalItems(jobId, effectiveEnd - effectiveStart);
    updateMessage(jobId, "Found " + comicUrls.size() + " comics. Processing " + (effectiveEnd - effectiveStart));

    // Process each comic URL
    for (var i = effectiveStart; i < effectiveEnd; i++) {
      checkPauseOrCancel(jobId, i);

      var comicUrl = comicUrls.get(i);
      var comicName = extractComicName(comicUrl);

      updateProgress(jobId, i - effectiveStart, effectiveEnd - effectiveStart, comicName);

      try {
        if (shouldSkipItem(i, settings)) {
          incrementSkipped(jobId);
          continue;
        }

        // Create child COMIC job via queue
        enqueueAndProcess(job.getId(), COMIC, singletonList(comicUrl), singletonList(comicName));
        incrementCompleted(jobId);
        saveCheckpoint(jobId, i);

      } catch (Exception e) {
        log.warn("Failed to create job for comic {}: {}", comicUrl, getRootCauseMessage(e));
        incrementFailed(jobId);
      }
    }

    updateMessage(jobId, "Category crawl completed. Created " + (effectiveEnd - effectiveStart) + " comic jobs");
  }

  @Override
  public CrawlType getSupportedType() {
    return CATEGORY;
  }

  // ===== Private methods =====

  private List<String> extractComicUrls(Document doc) {
    var urls = new ArrayList<String>();

    // Common selectors for comic listings
    var selectors = List.of(
        "div.items article a[href]",
        "div.list-truyen a.truyen-title",
        "div.comic-list a.comic-link",
        "div.manga-list a.title",
        "div.truyen-list h3 a",
        "div.list-stories a[href*='/truyen-tranh/']",
        "li.story-item a.story-title"
    );

    for (var selector : selectors) {
      var elements = doc.select(selector);
      for (var element : elements) {
        var href = element.absUrl("href");
        if (isNotBlank(href) && isComicUrl(href) && !urls.contains(href)) {
          urls.add(href);
        }
      }
      if (!urls.isEmpty()) {
        break;
      }
    }

    // Fallback: find all links that look like comic URLs
    if (urls.isEmpty()) {
      var allLinks = doc.select("a[href*='/truyen-tranh/']");
      for (var link : allLinks) {
        var href = link.absUrl("href");
        if (isNotBlank(href) && isComicUrl(href) && !urls.contains(href)) {
          urls.add(href);
        }
      }
    }

    return urls;
  }

  private boolean isComicUrl(String url) {
    return COMIC_URL_PATTERN.matcher(url).find();
  }

  private String extractComicName(String url) {
    var slug = extractSlugFromUrl(url);
    return isNotBlank(slug) ? slug.replace("-", SPACE) : url;
  }
}
