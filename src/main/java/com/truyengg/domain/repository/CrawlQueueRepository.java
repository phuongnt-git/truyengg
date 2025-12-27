package com.truyengg.domain.repository;

import com.truyengg.domain.entity.CrawlQueue;
import com.truyengg.domain.enums.QueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CrawlQueueRepository extends JpaRepository<CrawlQueue, UUID> {

  @Query("SELECT COUNT(q) FROM CrawlQueue q WHERE q.status = :status")
  long countByStatus(QueueStatus status);

  @Modifying
  @Query("UPDATE CrawlQueue q SET q.deletedAt = :now WHERE q.crawlJob.id IN :jobIds")
  void softDeleteByJobIds(Collection<UUID> jobIds, ZonedDateTime now);

  @Modifying
  @Query("UPDATE CrawlQueue q SET q.deletedAt = NULL WHERE q.crawlJob.id IN :jobIds")
  void restoreByJobIds(Collection<UUID> jobIds);

  @Query(value = """
      SELECT * FROM crawl_queue
      WHERE status = 'PENDING' AND deleted_at IS NULL
      ORDER BY priority DESC, created_at ASC
      LIMIT :limit
      FOR UPDATE SKIP LOCKED
      """, nativeQuery = true)
  List<CrawlQueue> findAndLockPending(int limit);

  @Query(value = """
      SELECT * FROM crawl_queue
      WHERE crawl_type = :type AND status = 'PENDING' AND deleted_at IS NULL
      ORDER BY priority DESC, created_at ASC
      LIMIT :limit
      FOR UPDATE SKIP LOCKED
      """, nativeQuery = true)
  List<CrawlQueue> findAndLockPendingByType(String type, int limit);
}

