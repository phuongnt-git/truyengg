package com.truyengg.service.crawl;

import com.truyengg.domain.entity.ChapterCrawl;
import com.truyengg.domain.enums.ComicCrawlStatus;
import com.truyengg.domain.repository.ChapterCrawlRepository;
import com.truyengg.domain.repository.ComicCrawlRepository;
import com.truyengg.model.dto.ChapterCrawlRetryStatistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.truyengg.domain.enums.ChapterCrawlStatus.COMPLETED;
import static com.truyengg.domain.enums.ChapterCrawlStatus.DOWNLOADING;
import static com.truyengg.domain.enums.ChapterCrawlStatus.FAILED;
import static com.truyengg.domain.enums.ChapterCrawlStatus.PENDING;
import static java.time.ZonedDateTime.now;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChapterCrawlService {

  private final ChapterCrawlRepository chapterCrawlRepository;
  private final ComicCrawlRepository comicCrawlRepository;

  @Transactional
  public void createChapterCrawl(UUID crawlId, Integer chapterIndex, String chapterUrl) {
    var comicCrawl = comicCrawlRepository.findById(crawlId)
        .orElseThrow(() -> new IllegalArgumentException("Comic crawl not found: " + crawlId));

    var existingOpt = chapterCrawlRepository.findByCrawlIdAndChapterIndex(crawlId, chapterIndex);
    if (existingOpt.isPresent()) {
      return;
    }

    var chapterCrawl = ChapterCrawl.builder()
        .crawl(comicCrawl)
        .chapterIndex(chapterIndex)
        .chapterUrl(chapterUrl)
        .status(PENDING)
        .retryCount(0)
        .totalImages(0)
        .downloadedImages(0)
        .build();

    chapterCrawlRepository.save(chapterCrawl);
  }

  @Transactional
  public void incrementRetryCount(UUID crawlId, Integer chapterIndex) {
    var chapterCrawlOpt = chapterCrawlRepository.findByCrawlIdAndChapterIndex(crawlId, chapterIndex);
    if (chapterCrawlOpt.isPresent()) {
      var chapterCrawl = chapterCrawlOpt.get();
      chapterCrawl.setRetryCount(chapterCrawl.getRetryCount() + 1);
      chapterCrawlRepository.save(chapterCrawl);
      log.info("Incremented retry count for crawl {} chapter {} to {}", crawlId, chapterIndex, chapterCrawl.getRetryCount());
    } else {
      log.warn("Detail not found for crawl {} chapter {}, cannot increment retry count", crawlId, chapterIndex);
    }
  }

  @Transactional
  public void updateChapterCrawlProgress(UUID crawlId, Integer chapterIndex, Integer downloadedImages,
                                         Integer totalImages, Integer requestCount, Integer errorCount) {
    var chapterCrawlOpt = chapterCrawlRepository.findByCrawlIdAndChapterIndex(crawlId, chapterIndex);
    if (chapterCrawlOpt.isPresent()) {
      var chapterCrawl = chapterCrawlOpt.get();
      chapterCrawl.setDownloadedImages(downloadedImages);
      chapterCrawl.setTotalImages(totalImages);
      if (requestCount != null) {
        chapterCrawl.setRequestCount(requestCount);
      }
      if (errorCount != null) {
        chapterCrawl.setErrorCount(errorCount);
      }
      if (chapterCrawl.getStartedAt() == null) {
        chapterCrawl.setStartedAt(now());
      }
      chapterCrawl.setStatus(DOWNLOADING);
      chapterCrawlRepository.save(chapterCrawl);
    }
  }

  @Transactional
  public void markChapterCrawlCompleted(UUID crawlId, Integer chapterIndex, List<String> imagePaths,
                                        List<String> originalImagePaths, Long fileSizeBytes, Long downloadTimeSeconds,
                                        Integer totalImages, Integer downloadedImages, Integer requestCount, Integer errorCount) {
    var chapterCrawlOpt = chapterCrawlRepository.findByCrawlIdAndChapterIndex(crawlId, chapterIndex);
    if (chapterCrawlOpt.isEmpty()) {
      log.warn("Not found for crawl {} chapter {}, cannot mark as completed", crawlId, chapterIndex);
      return;
    }

    var chapterCrawl = chapterCrawlOpt.get();
    chapterCrawl.setStatus(COMPLETED);
    chapterCrawl.setImagePaths(imagePaths);
    chapterCrawl.setOriginalImagePaths(originalImagePaths);
    chapterCrawl.setFileSizeBytes(fileSizeBytes);
    chapterCrawl.setDownloadTimeSeconds(downloadTimeSeconds);
    chapterCrawl.setTotalImages(totalImages);
    chapterCrawl.setDownloadedImages(downloadedImages);
    if (requestCount != null) {
      chapterCrawl.setRequestCount(requestCount);
    }
    if (errorCount != null) {
      chapterCrawl.setErrorCount(errorCount);
    }
    chapterCrawl.setCompletedAt(now());

    if (downloadTimeSeconds != null && downloadTimeSeconds > 0 && fileSizeBytes != null && fileSizeBytes > 0) {
      var speed = BigDecimal.valueOf(fileSizeBytes)
          .divide(BigDecimal.valueOf(downloadTimeSeconds), 2, RoundingMode.HALF_UP);
      chapterCrawl.setDownloadSpeedBytesPerSecond(speed);
    }

    chapterCrawlRepository.save(chapterCrawl);
  }

  @Transactional
  public void markChapterCrawlFailed(UUID crawlId, Integer chapterIndex, List<String> errorMessages) {
    var chapterCrawlOpt = chapterCrawlRepository.findByCrawlIdAndChapterIndex(crawlId, chapterIndex);
    if (chapterCrawlOpt.isEmpty()) {
      log.warn("Not found for crawl {} chapter {}, cannot mark as failed", crawlId, chapterIndex);
      return;
    }

    var chapterCrawl = chapterCrawlOpt.get();
    chapterCrawl.setStatus(FAILED);
    if (errorMessages != null && !errorMessages.isEmpty()) {
      var existingErrors = chapterCrawl.getErrorMessages() != null ? new ArrayList<>(chapterCrawl.getErrorMessages()) : new ArrayList<String>();
      existingErrors.addAll(errorMessages);
      chapterCrawl.setErrorMessages(existingErrors);
    }
    chapterCrawl.setCompletedAt(now());
    chapterCrawlRepository.save(chapterCrawl);
  }

  @Transactional(readOnly = true)
  public Page<ChapterCrawl> getChapterCrawlByCrawlId(UUID crawlId, Pageable pageable) {
    return chapterCrawlRepository.findByCrawlIdOrderByChapterIndex(crawlId, pageable);
  }

  @Transactional(readOnly = true)
  public List<ChapterCrawl> getChapterCrawlByCrawlIdAll(UUID crawlId) {
    return chapterCrawlRepository.findByCrawlIdOrderByChapterIndex(crawlId);
  }

  @Transactional(readOnly = true)
  public long countCompletedChapterCrawls(UUID crawlId) {
    return chapterCrawlRepository.countByCrawlIdAndStatus(crawlId, COMPLETED);
  }

  @Transactional(readOnly = true)
  public long countTotalChapterCrawls(UUID crawlId) {
    return chapterCrawlRepository.countByCrawlId(crawlId);
  }

  @Transactional
  public void aggregateChapterCrawlMetrics(UUID crawlId) {
    var crawlOpt = comicCrawlRepository.findById(crawlId);
    if (crawlOpt.isEmpty()) {
      log.warn("Crawl not found for metrics aggregation: {}", crawlId);
      return;
    }

    var crawl = crawlOpt.get();
    var totalFileSize = chapterCrawlRepository.sumFileSizeByCrawlId(crawlId);
    var totalDownloadTime = chapterCrawlRepository.sumDownloadTimeByCrawlId(crawlId);
    var totalRequestCount = chapterCrawlRepository.sumRequestCountByCrawlId(crawlId);
    var totalErrorCount = chapterCrawlRepository.sumErrorCountByCrawlId(crawlId);
    var avgDownloadSpeed = chapterCrawlRepository.findAvgDownloadSpeedByCrawlId(crawlId);

    crawl.setTotalFileSizeBytes(totalFileSize != null ? totalFileSize : 0L);
    crawl.setTotalDownloadTimeSeconds(totalDownloadTime != null ? totalDownloadTime : 0L);
    crawl.setTotalRequestCount(totalRequestCount != null ? totalRequestCount.intValue() : 0);
    crawl.setTotalErrorCount(totalErrorCount != null ? totalErrorCount.intValue() : 0);
    crawl.setAverageDownloadSpeedBytesPerSecond(avgDownloadSpeed);

    comicCrawlRepository.save(crawl);
  }

  @Transactional(readOnly = true)
  public ChapterCrawlRetryStatistics getRetryStatisticsByCrawlId(UUID crawlId) {
    var maxRetry = chapterCrawlRepository.findMaxRetryCountByCrawlId(crawlId);
    var avgRetry = chapterCrawlRepository.findAvgRetryCountByCrawlId(crawlId);
    var chaptersWithRetry = chapterCrawlRepository.countChaptersWithRetryByCrawlId(crawlId);
    var failedChapters = chapterCrawlRepository.countByCrawlIdAndStatus(crawlId, FAILED);

    return new ChapterCrawlRetryStatistics(
        maxRetry != null ? maxRetry : 0,
        avgRetry != null ? avgRetry.doubleValue() : 0.0,
        chaptersWithRetry != null ? chaptersWithRetry : 0L,
        failedChapters
    );
  }

  @Transactional
  public void cleanupOldChapterCrawls(int daysOld) {
    var cutoffDate = now().minusDays(daysOld);
    var allDetails = chapterCrawlRepository.findAll();

    var deletedCount = 0;
    for (var detail : allDetails) {
      var crawl = detail.getCrawl();
      if (crawl != null) {
        var createdAt = detail.getCreatedAt();
        var crawlStatus = crawl.getStatus();
        if ((crawlStatus == ComicCrawlStatus.COMPLETED ||
            crawlStatus == ComicCrawlStatus.FAILED ||
            crawlStatus == ComicCrawlStatus.CANCELLED) &&
            createdAt != null && createdAt.isBefore(cutoffDate)) {
          try {
            chapterCrawlRepository.delete(detail);
            deletedCount++;
          } catch (Exception e) {
            log.warn("Failed to delete old detail {}: {}", detail.getId(), e.getMessage());
          }
        }
      }
    }

    if (deletedCount > 0) {
      log.info("Cleaned up {} old chapter crawl (older than {} days)", deletedCount, daysOld);
    }
  }

}

