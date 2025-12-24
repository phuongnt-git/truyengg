package com.truyengg.domain.repository;

import com.truyengg.domain.entity.CrawlEvent;
import com.truyengg.domain.enums.CrawlEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CrawlEventRepository extends JpaRepository<CrawlEvent, Long> {

  @Query("SELECT e FROM CrawlEvent e WHERE e.crawl.id = :crawlId ORDER BY e.createdAt DESC")
  List<CrawlEvent> findByCrawlIdOrderByCreatedAtDesc(UUID crawlId);

  @Query("SELECT e FROM CrawlEvent e WHERE e.crawl.id = :crawlId AND e.eventType = :eventType ORDER BY e.createdAt DESC")
  List<CrawlEvent> findByCrawlIdAndEventTypeOrderByCreatedAtDesc(UUID crawlId, CrawlEventType eventType);

  @Query("SELECT e FROM CrawlEvent e WHERE e.crawl.id = :crawlId AND e.eventType = :eventType ORDER BY e.createdAt DESC")
  Optional<CrawlEvent> findLatestByCrawlIdAndEventType(UUID crawlId, CrawlEventType eventType);

  @Query("SELECT COUNT(e) FROM CrawlEvent e WHERE e.crawl.id = :crawlId AND e.eventType = :eventType")
  long countByCrawlIdAndEventType(UUID crawlId, CrawlEventType eventType);
}

