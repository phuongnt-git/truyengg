package com.truyengg.domain.repository;

import com.truyengg.domain.entity.ComicCrawlCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComicCrawlCheckpointRepository extends JpaRepository<ComicCrawlCheckpoint, Long> {

  @Query("SELECT c FROM ComicCrawlCheckpoint c WHERE c.crawl.id = :crawlId")
  Optional<ComicCrawlCheckpoint> findByCrawlId(UUID crawlId);

  @Modifying
  @Query("DELETE FROM ComicCrawlCheckpoint c WHERE c.crawl.id = :crawlId")
  void deleteByCrawlId(UUID crawlId);
}

