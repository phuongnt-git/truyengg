package com.truyengg.domain.repository;

import com.truyengg.domain.entity.CrawlProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CrawlProgressRepository extends JpaRepository<CrawlProgress, Long> {

  @Query("SELECT c FROM CrawlProgress c WHERE c.crawl.id = :crawlId")
  Optional<CrawlProgress> findByCrawlId(UUID crawlId);

  @Modifying
  @Query("DELETE FROM CrawlProgress c WHERE c.crawl.id = :crawlId")
  void deleteByCrawlId(UUID crawlId);

  @Modifying
  @Query("""
      UPDATE CrawlProgress c
      SET c.currentChapter = :currentChapter,
          c.totalChapters = :totalChapters,
          c.downloadedImages = :downloadedImages,
          c.totalImages = :totalImages,
          c.currentMessage = :currentMessage,
          c.messages = :messages,
          c.lastUpdate = :lastUpdate,
          c.elapsedSeconds = :elapsedSeconds
      WHERE c.crawl.id = :crawlId
      """)
  void updateProgress(
      UUID crawlId,
      Integer currentChapter,
      Integer totalChapters,
      Integer downloadedImages,
      Integer totalImages,
      String currentMessage,
      List<String> messages,
      java.time.ZonedDateTime lastUpdate,
      Long elapsedSeconds
  );
}

