package com.truyengg.service.crawl;

import com.truyengg.domain.entity.ChapterCrawl;
import com.truyengg.domain.entity.ComicCrawl;
import com.truyengg.model.dto.ChapterCrawlDto;
import com.truyengg.model.dto.ChapterCrawlRetryStatistics;
import com.truyengg.model.dto.ComicCrawlMetricsDto;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;

import static java.lang.String.format;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;

@UtilityClass
public class ChapterCrawlMapper {

  public ChapterCrawlDto toDto(ChapterCrawl chapterCrawl) {
    return new ChapterCrawlDto(
        chapterCrawl.getId(),
        chapterCrawl.getChapterIndex(),
        chapterCrawl.getChapterUrl(),
        chapterCrawl.getChapterName(),
        chapterCrawl.getStatus(),
        chapterCrawl.getTotalImages(),
        chapterCrawl.getDownloadedImages(),
        chapterCrawl.getFileSizeBytes(),
        formatFileSize(chapterCrawl.getFileSizeBytes()),
        chapterCrawl.getDownloadTimeSeconds(),
        formatDuration(chapterCrawl.getDownloadTimeSeconds()),
        chapterCrawl.getRequestCount(),
        chapterCrawl.getErrorCount(),
        chapterCrawl.getDownloadSpeedBytesPerSecond(),
        formatSpeed(chapterCrawl.getDownloadSpeedBytesPerSecond()),
        chapterCrawl.getRetryCount(),
        chapterCrawl.getErrorMessages(),
        chapterCrawl.getImagePaths(),
        chapterCrawl.getOriginalImagePaths(),
        chapterCrawl.getStartedAt(),
        chapterCrawl.getCompletedAt(),
        chapterCrawl.getCreatedAt(),
        chapterCrawl.getUpdatedAt()
    );
  }

  private String formatFileSize(Long bytes) {
    if (isEmpty(bytes) || bytes == 0) {
      return "0 B";
    }

    double size = bytes.doubleValue();
    if (size < 1024) {
      return format("%.0f B", size);
    } else if (size < 1024 * 1024) {
      return format("%.2f KB", size / 1024);
    } else if (size < 1024 * 1024 * 1024) {
      return format("%.2f MB", size / (1024 * 1024));
    } else {
      return format("%.2f GB", size / (1024 * 1024 * 1024));
    }
  }

  private String formatDuration(Long seconds) {
    if (isEmpty(seconds) || seconds == 0) {
      return "0 giây";
    }

    var hours = seconds / 3600;
    var minutes = (seconds % 3600) / 60;
    var secs = seconds % 60;

    if (hours > 0) {
      return format("%d giờ %d phút %d giây", hours, minutes, secs);
    } else if (minutes > 0) {
      return format("%d phút %d giây", minutes, secs);
    } else {
      return format("%d giây", secs);
    }
  }

  private String formatSpeed(BigDecimal bytesPerSecond) {
    if (isEmpty(bytesPerSecond) || bytesPerSecond.compareTo(BigDecimal.ZERO) == 0) {
      return "0 B/s";
    }

    var speed = bytesPerSecond.doubleValue();
    if (speed < 1024) {
      return format("%.2f B/s", speed);
    } else if (speed < 1024 * 1024) {
      return format("%.2f KB/s", speed / 1024);
    } else if (speed < 1024 * 1024 * 1024) {
      return format("%.2f MB/s", speed / (1024 * 1024));
    } else {
      return format("%.2f GB/s", speed / (1024 * 1024 * 1024));
    }
  }

  public ComicCrawlMetricsDto toMetricsDto(ComicCrawl crawl, ChapterCrawlRetryStatistics retryStats) {
    return new ComicCrawlMetricsDto(
        crawl.getTotalFileSizeBytes(),
        formatFileSize(crawl.getTotalFileSizeBytes()),
        crawl.getTotalDownloadTimeSeconds(),
        formatDuration(crawl.getTotalDownloadTimeSeconds()),
        crawl.getTotalRequestCount(),
        crawl.getTotalErrorCount(),
        crawl.getAverageDownloadSpeedBytesPerSecond(),
        formatSpeed(crawl.getAverageDownloadSpeedBytesPerSecond()),
        retryStats.maxRetryCount(),
        retryStats.avgRetryCount(),
        retryStats.chaptersWithRetry(),
        retryStats.failedChaptersCount()
    );
  }
}

