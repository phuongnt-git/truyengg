package com.truyengg.service.crawl;

import com.truyengg.domain.entity.CrawlJob;
import com.truyengg.domain.enums.CrawlStatus;
import com.truyengg.domain.repository.ChapterRepository;
import com.truyengg.domain.repository.ComicRepository;
import com.truyengg.domain.repository.CrawlJobRepository;
import com.truyengg.model.dto.DuplicateCheckResult;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.truyengg.model.dto.DuplicateCheckResult.contentHash;
import static com.truyengg.model.dto.DuplicateCheckResult.exactUrl;
import static com.truyengg.model.dto.DuplicateCheckResult.noDuplicate;
import static com.truyengg.model.dto.DuplicateCheckResult.similarUrl;
import static com.truyengg.service.crawl.SlugExtractor.extractFromUrl;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Service for detecting duplicate content.
 * Implements Decision 6: URL first (fast), content hash optional (expensive).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DuplicateDetectionService {

  private static final List<CrawlStatus> ACTIVE_STATUSES = List.of(
      CrawlStatus.PENDING, CrawlStatus.RUNNING, CrawlStatus.PAUSED
  );
  ComicRepository comicRepository;
  ChapterRepository chapterRepository;
  CrawlJobRepository crawlJobRepository;
  CrawlHttpClient httpClient;

  @Value("${crawl.duplicate.check-content-hash:false}")
  boolean checkContentHashEnabled = false;

  /**
   * Check for duplicates - URL first, then optional content hash.
   */
  public DuplicateCheckResult checkDuplicate(String url, boolean includeContentHash) {
    // Step 1: Normalize URL
    var normalizedUrl = normalizeUrl(url);

    // Step 2: Exact URL match (fastest)
    var exactMatch = comicRepository.findBySource(normalizedUrl);
    if (exactMatch.isPresent()) {
      var comic = exactMatch.get();
      var activeJob = findActiveJobForContent(comic.getId());
      var chapterCount = (int) chapterRepository.countByComic(comic);
      return exactUrl(
          activeJob.map(CrawlJob::getId).orElse(null),
          comic.getId(),
          comic.getSource(),
          chapterCount
      );
    }

    // Step 3: Slug match (same comic different mirror)
    var slug = extractFromUrl(url);
    if (isNotBlank(slug)) {
      var slugMatch = comicRepository.findBySlug(slug);
      if (slugMatch.isPresent()) {
        var comic = slugMatch.get();
        var activeJob = findActiveJobForContent(comic.getId());
        var chapterCount = (int) chapterRepository.countByComic(comic);
        return similarUrl(
            activeJob.map(CrawlJob::getId).orElse(null),
            comic.getId(),
            comic.getSource(),
            chapterCount
        );
      }
    }

    // Step 4: Content hash (optional, expensive)
    if (includeContentHash && checkContentHashEnabled) {
      var contentMatch = checkByContentHash(url);
      if (contentMatch.isPresent()) {
        return contentMatch.get();
      }
    }

    return noDuplicate();
  }

  /**
   * Check for duplicates with default settings.
   */
  public DuplicateCheckResult checkDuplicate(String url) {
    return checkDuplicate(url, false);
  }

  /**
   * Batch check for category crawl efficiency.
   * Uses individual lookups since batch methods aren't available.
   */
  public Map<String, DuplicateCheckResult> batchCheckDuplicates(Collection<String> urls) {
    Map<String, DuplicateCheckResult> results = new HashMap<>();

    for (var url : urls) {
      var normalized = normalizeUrl(url);

      // Check exact match by source URL
      var exactMatch = comicRepository.findBySource(normalized);
      if (exactMatch.isPresent()) {
        var comic = exactMatch.get();
        var chapterCount = (int) chapterRepository.countByComic(comic);
        results.put(url, exactUrl(
            null, comic.getId(), comic.getSource(), chapterCount
        ));
        continue;
      }

      // Check slug match
      var slug = extractFromUrl(url);
      if (isNotBlank(slug)) {
        var slugMatch = comicRepository.findBySlug(slug);
        if (slugMatch.isPresent()) {
          var comic = slugMatch.get();
          var chapterCount = (int) chapterRepository.countByComic(comic);
          results.put(url, similarUrl(
              null, comic.getId(), comic.getSource(), chapterCount
          ));
          continue;
        }
      }

      // No duplicate found
      results.put(url, noDuplicate());
    }

    return results;
  }

  /**
   * Check if URL already has an active crawl job.
   */
  public Optional<CrawlJob> findActiveCrawlForUrl(String url) {
    var normalizedUrl = normalizeUrl(url);
    return crawlJobRepository.findFirstByTargetUrlAndStatusInOrderByCreatedAtDesc(
        normalizedUrl, ACTIVE_STATUSES
    );
  }

  /**
   * Get summary of batch check results.
   */
  public BatchCheckSummary summarizeBatchCheck(Map<String, DuplicateCheckResult> results) {
    var total = results.size();
    var exactMatches = 0;
    var similarMatches = 0;
    var noDuplicates = 0;

    for (var result : results.values()) {
      switch (result.type()) {
        case EXACT_URL -> exactMatches++;
        case SIMILAR_URL -> similarMatches++;
        case NO_DUPLICATE -> noDuplicates++;
        default -> {
        }
      }
    }

    return new BatchCheckSummary(total, exactMatches, similarMatches, noDuplicates);
  }

  // ===== Private methods =====

  private String normalizeUrl(String url) {
    if (isBlank(url)) return EMPTY;
    return url.toLowerCase()
        .replaceAll("https?://", "")
        .replaceAll("www\\.", "")
        .replaceAll("/$", "")
        .replaceAll("\\?.*$", "");  // Remove query params
  }

  private Optional<CrawlJob> findActiveJobForContent(long contentId) {
    return crawlJobRepository.findFirstByContentIdAndStatusInOrderByCreatedAtDesc(
        contentId, ACTIVE_STATUSES
    );
  }

  /**
   * Check by content hash (compare cover image hash).
   * Expensive - only used when explicitly requested.
   */
  private Optional<DuplicateCheckResult> checkByContentHash(String url) {
    try {
      // Step 1: Fetch comic page and extract cover URL
      var coverUrl = extractCoverUrl(url);
      if (isBlank(coverUrl)) {
        log.debug("Could not extract cover URL from: {}", url);
        return Optional.empty();
      }

      // Step 2: Download cover and calculate hash
      var domain = httpClient.extractDomainFromUrl(url);
      var headers = httpClient.buildHeaders(domain);
      var coverBytes = httpClient.downloadImage(coverUrl, headers);

      if (coverBytes == null || coverBytes.length == 0) {
        log.debug("Could not download cover image from: {}", coverUrl);
        return Optional.empty();
      }

      var hash = DigestUtils.sha256Hex(coverBytes);

      // Step 3: Find existing comic with same cover hash
      var existing = comicRepository.findByCoverHash(hash);
      if (existing.isPresent()) {
        var comic = existing.get();
        var activeJob = findActiveJobForContent(comic.getId());
        var chapterCount = (int) chapterRepository.countByComic(comic);
        return Optional.of(contentHash(
            activeJob.map(CrawlJob::getId).orElse(null),
            comic.getId(),
            comic.getSource(),
            chapterCount
        ));
      }
    } catch (Exception e) {
      log.warn("Content hash check failed for {}: {}", url, e.getMessage());
    }
    return Optional.empty();
  }

  /**
   * Extract cover image URL from comic page.
   */
  private String extractCoverUrl(String url) {
    try {
      var domain = httpClient.extractDomainFromUrl(url);
      var headers = httpClient.buildHeaders(domain);
      var html = httpClient.fetchUrl(url, headers, false);

      if (isBlank(html)) {
        return null;
      }

      var doc = Jsoup.parse(html);

      // Try common selectors for cover image
      var selectors = List.of(
          "meta[property=og:image]",
          "div.book img",
          "div.comic-info img",
          "div.detail img",
          "div.cover img",
          "img.cover",
          "img.thumb"
      );

      for (var selector : selectors) {
        var element = doc.selectFirst(selector);
        if (element != null) {
          var imgUrl = selector.contains("meta")
              ? element.attr("content")
              : element.attr("src");

          if (isBlank(imgUrl)) {
            imgUrl = element.attr("data-src");
          }
          if (isBlank(imgUrl)) {
            imgUrl = element.attr("data-original");
          }

          if (isNotBlank(imgUrl)) {
            // Normalize URL
            if (imgUrl.startsWith("//")) {
              return "https:" + imgUrl;
            } else if (imgUrl.startsWith("/")) {
              return domain + imgUrl;
            }
            return imgUrl;
          }
        }
      }
    } catch (Exception e) {
      log.warn("Failed to extract cover URL from {}: {}", url, e.getMessage());
    }
    return null;
  }

  /**
   * Summary of batch duplicate check.
   */
  public record BatchCheckSummary(
      int total,
      int exactMatches,
      int similarMatches,
      int noDuplicates
  ) {
    public int getDuplicateCount() {
      return exactMatches + similarMatches;
    }

    public double getDuplicatePercentage() {
      if (total == 0) return 0;
      return (getDuplicateCount() * 100.0) / total;
    }
  }
}

