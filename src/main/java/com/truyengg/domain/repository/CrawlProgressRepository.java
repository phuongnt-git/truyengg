package com.truyengg.domain.repository;

import com.truyengg.domain.entity.CrawlProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.UUID;

@Repository
public interface CrawlProgressRepository extends JpaRepository<CrawlProgress, UUID> {

  @Modifying
  @Query("UPDATE CrawlProgress p SET p.deletedAt = :now WHERE p.id IN :jobIds")
  void softDeleteByJobIds(Collection<UUID> jobIds, ZonedDateTime now);

  @Modifying
  @Query("UPDATE CrawlProgress p SET p.deletedAt = NULL WHERE p.id IN :jobIds")
  void restoreByJobIds(Collection<UUID> jobIds);

  @Modifying
  @Query("""
      UPDATE CrawlProgress p
      SET p.completedItems = p.completedItems + 1,
          p.lastUpdateAt = :now,
          p.percent = CASE WHEN p.totalItems > 0 THEN ((p.completedItems + 1) * 100 / p.totalItems) ELSE 0 END
      WHERE p.id = :jobId
      """)
  void incrementCompletedItems(UUID jobId, ZonedDateTime now);

  @Modifying
  @Query("""
      UPDATE CrawlProgress p
      SET p.bytesDownloaded = p.bytesDownloaded + :bytes,
          p.lastUpdateAt = :now
      WHERE p.id = :jobId
      """)
  void addBytesDownloaded(UUID jobId, long bytes, ZonedDateTime now);
}
