package com.truyengg.domain.repository;

import com.truyengg.domain.entity.ChapterCrawl;
import com.truyengg.domain.enums.ChapterCrawlStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChapterCrawlRepository extends JpaRepository<ChapterCrawl, Long> {

  @Query("SELECT d FROM ChapterCrawl d WHERE d.crawl.id = :crawlId ORDER BY d.chapterIndex ASC")
  Page<ChapterCrawl> findByCrawlIdOrderByChapterIndex(UUID crawlId, Pageable pageable);

  @Query("SELECT d FROM ChapterCrawl d WHERE d.crawl.id = :crawlId ORDER BY d.chapterIndex ASC")
  List<ChapterCrawl> findByCrawlIdOrderByChapterIndex(UUID crawlId);

  @Query("SELECT COUNT(d) FROM ChapterCrawl d WHERE d.crawl.id = :crawlId")
  long countByCrawlId(UUID crawlId);

  @Query("SELECT COUNT(d) FROM ChapterCrawl d WHERE d.crawl.id = :crawlId AND d.status = :status")
  long countByCrawlIdAndStatus(UUID crawlId, ChapterCrawlStatus status);

  @Query("SELECT d FROM ChapterCrawl d WHERE d.crawl.id = :crawlId AND d.chapterIndex = :chapterIndex")
  Optional<ChapterCrawl> findByCrawlIdAndChapterIndex(UUID crawlId, Integer chapterIndex);

  @Query("SELECT COALESCE(SUM(d.fileSizeBytes), 0) FROM ChapterCrawl d WHERE d.crawl.id = :crawlId")
  Long sumFileSizeByCrawlId(UUID crawlId);

  @Query("SELECT COALESCE(SUM(d.downloadTimeSeconds), 0) FROM ChapterCrawl d WHERE d.crawl.id = :crawlId")
  Long sumDownloadTimeByCrawlId(UUID crawlId);

  @Query("SELECT COALESCE(SUM(d.requestCount), 0) FROM ChapterCrawl d WHERE d.crawl.id = :crawlId")
  Long sumRequestCountByCrawlId(UUID crawlId);

  @Query("SELECT COALESCE(SUM(d.errorCount), 0) FROM ChapterCrawl d WHERE d.crawl.id = :crawlId")
  Long sumErrorCountByCrawlId(UUID crawlId);

  @Query("SELECT COALESCE(MAX(d.retryCount), 0) FROM ChapterCrawl d WHERE d.crawl.id = :crawlId")
  Integer findMaxRetryCountByCrawlId(UUID crawlId);

  @Query("SELECT COALESCE(AVG(d.retryCount), 0) FROM ChapterCrawl d WHERE d.crawl.id = :crawlId")
  BigDecimal findAvgRetryCountByCrawlId(UUID crawlId);

  @Query("SELECT COUNT(d) FROM ChapterCrawl d WHERE d.crawl.id = :crawlId AND d.retryCount > 0")
  Long countChaptersWithRetryByCrawlId(UUID crawlId);

  @Query("SELECT COALESCE(AVG(d.downloadSpeedBytesPerSecond), 0) FROM ChapterCrawl d WHERE d.crawl.id = :crawlId AND d.downloadSpeedBytesPerSecond IS NOT NULL")
  BigDecimal findAvgDownloadSpeedByCrawlId(UUID crawlId);
}

