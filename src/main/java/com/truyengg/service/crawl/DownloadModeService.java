package com.truyengg.service.crawl;

import com.truyengg.domain.entity.Chapter;
import com.truyengg.domain.entity.ChapterImage;
import com.truyengg.domain.entity.CrawlJob;
import com.truyengg.domain.entity.CrawlSettings;
import com.truyengg.domain.enums.DownloadMode;
import com.truyengg.domain.repository.ChapterImageRepository;
import com.truyengg.domain.repository.ChapterRepository;
import com.truyengg.domain.repository.ComicRepository;
import com.truyengg.domain.repository.CrawlSettingsRepository;
import com.truyengg.model.dto.DuplicateCheckResult;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.truyengg.domain.enums.DownloadMode.FULL;
import static com.truyengg.domain.enums.DownloadMode.NONE;
import static com.truyengg.domain.enums.DownloadMode.UPDATE;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.IntStream.range;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Service for determining which items to download based on download mode.
 * Implements Decision 7: FULL/UPDATE/PARTIAL/NONE modes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DownloadModeService {

  ComicRepository comicRepository;
  ChapterRepository chapterRepository;
  ChapterImageRepository imageRepository;
  CrawlSettingsRepository settingsRepository;

  /**
   * Determine which items to download based on mode and settings.
   *
   * @param job            The crawl job
   * @param sourceItemUrls List of URLs discovered from source
   * @return List of indices to download
   */
  public List<Integer> determineItemsToDownload(CrawlJob job, List<String> sourceItemUrls) {
    var settings = settingsRepository.findById(job.getId()).orElse(null);
    var totalItems = sourceItemUrls.size();

    return switch (job.getDownloadMode()) {
      case FULL -> {
        // Download all items
        var indices = range(0, totalItems).boxed()
            .collect(toCollection(ArrayList::new));

        // Apply skip list if present
        if (settings != null && isNotEmpty(settings.getSkipItems())) {
          indices.removeAll(settings.getSkipItems());
        }

        yield indices;
      }

      case UPDATE -> {
        // Only new items (not in DB)
        var existingUrls = getExistingItemUrls(job);

        yield range(0, totalItems)
            .filter(i -> !existingUrls.contains(normalizeUrl(sourceItemUrls.get(i))))
            .boxed()
            .toList();
      }

      case PARTIAL -> {
        if (settings == null) {
          yield emptyList();
        }

        // Use range and/or specific indices
        int start = settings.getRangeStart() >= 0 ? settings.getRangeStart() : 0;
        int end = settings.getRangeEnd() >= 0 ?
            Math.min(settings.getRangeEnd(), totalItems - 1) : totalItems - 1;

        var indices = IntStream.rangeClosed(start, end).boxed()
            .collect(toCollection(ArrayList::new));

        // Add redownload items (force include)
        if (isNotEmpty(settings.getRedownloadItems())) {
          for (int idx : settings.getRedownloadItems()) {
            if (idx >= 0 && idx < totalItems && !indices.contains(idx)) {
              indices.add(idx);
            }
          }
          Collections.sort(indices);
        }

        // Remove skip items
        if (isNotEmpty(settings.getSkipItems())) {
          indices.removeAll(settings.getSkipItems());
        }

        yield indices;
      }

      case NONE -> // Skip all - for duplicates that user chose to skip
          emptyList();
    };
  }

  /**
   * Suggest download mode based on duplicate check result.
   */
  public DownloadMode suggestMode(DuplicateCheckResult duplicateResult) {
    if (!duplicateResult.hasDuplicate()) {
      return FULL;
    }

    // Has duplicate - suggest UPDATE to only get new chapters
    if (duplicateResult.existingChapterCount() > 0) {
      return UPDATE;
    }

    // Duplicate exists but no chapters - suggest FULL
    return FULL;
  }

  /**
   * Create settings for append-to-existing scenario.
   */
  public CrawlSettings createUpdateSettings(
      long existingComicId,
      List<Integer> specificChapters
  ) {
    var settings = CrawlSettings.builder()
        .build();

    if (isNotEmpty(specificChapters)) {
      settings.setRedownloadItems(specificChapters);
    }

    return settings;
  }

  /**
   * Check if an item should be downloaded based on settings.
   */
  public boolean shouldDownloadItem(CrawlJob job, int itemIndex, String itemUrl) {
    var settings = settingsRepository.findById(job.getId()).orElse(null);

    // Check skip list
    if (settings != null && isNotEmpty(settings.getSkipItems())) {
      if (settings.getSkipItems().contains(itemIndex)) {
        return false;
      }
    }

    // Check redownload list (force include)
    if (settings != null && isNotEmpty(settings.getRedownloadItems())) {
      if (settings.getRedownloadItems().contains(itemIndex)) {
        return true;
      }
    }

    // Check range
    if (settings != null && settings.hasRange()) {
      if (settings.getRangeStart() >= 0 && itemIndex < settings.getRangeStart()) {
        return false;
      }
      if (settings.getRangeEnd() >= 0 && itemIndex > settings.getRangeEnd()) {
        return false;
      }
    }

    // For UPDATE mode, check if exists in DB
    if (job.getDownloadMode() == UPDATE) {
      var existingUrls = getExistingItemUrls(job);
      return !existingUrls.contains(normalizeUrl(itemUrl));
    }

    return job.getDownloadMode() != NONE;
  }

  /**
   * Get the count of items that will be downloaded.
   */
  public int countItemsToDownload(CrawlJob job, List<String> sourceItemUrls) {
    return determineItemsToDownload(job, sourceItemUrls).size();
  }

  // ===== Private methods =====

  private Set<String> getExistingItemUrls(CrawlJob job) {
    return switch (job.getCrawlType()) {
      case COMIC -> {
        // Get existing chapter URLs for this comic
        if (job.getContentId() <= 0) yield emptySet();
        var comic = comicRepository.findById(job.getContentId()).orElse(null);
        if (comic == null) yield emptySet();
        yield chapterRepository.findByComic(comic).stream()
            .map(Chapter::getSource)
            .filter(StringUtils::isNotBlank)
            .map(this::normalizeUrl)
            .collect(Collectors.toUnmodifiableSet());
      }
      case CHAPTER -> {
        // Get existing image URLs for this chapter
        if (job.getContentId() <= 0) yield emptySet();
        yield imageRepository.findByChapterIdOrderByImageOrderAsc(job.getContentId()).stream()
            .map(ChapterImage::getOriginalUrl)
            .filter(StringUtils::isNotBlank)
            .map(this::normalizeUrl)
            .collect(Collectors.toUnmodifiableSet());
      }
      default -> emptySet();
    };
  }

  private String normalizeUrl(String url) {
    if (isBlank(url)) return EMPTY;
    return url.toLowerCase()
        .replaceAll("https?://", "")
        .replaceAll("www\\.", "")
        .replaceAll("/$", "");
  }
}

