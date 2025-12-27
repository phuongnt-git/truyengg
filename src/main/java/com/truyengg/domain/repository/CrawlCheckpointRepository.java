package com.truyengg.domain.repository;

import com.truyengg.domain.entity.CrawlCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.UUID;

@Repository
public interface CrawlCheckpointRepository extends JpaRepository<CrawlCheckpoint, UUID> {

  @Modifying
  @Query("UPDATE CrawlCheckpoint c SET c.deletedAt = :now WHERE c.id IN :jobIds")
  void softDeleteByJobIds(Collection<UUID> jobIds, ZonedDateTime now);

  @Modifying
  @Query("UPDATE CrawlCheckpoint c SET c.deletedAt = NULL WHERE c.id IN :jobIds")
  void restoreByJobIds(Collection<UUID> jobIds);

  @Modifying
  @Query("UPDATE CrawlCheckpoint c SET c.lastItemIndex = :index, c.updatedAt = :now WHERE c.id = :jobId")
  void updateLastItemIndex(UUID jobId, int index, ZonedDateTime now);

  @Modifying
  @Query("""
      UPDATE CrawlCheckpoint c 
      SET c.pausedAt = :now, 
          c.updatedAt = :now 
      WHERE c.id = :jobId
      """)
  void recordPause(UUID jobId, ZonedDateTime now);

  @Modifying
  @Query("""
      UPDATE CrawlCheckpoint c 
      SET c.resumedAt = :now, 
          c.resumeCount = c.resumeCount + 1, 
          c.updatedAt = :now 
      WHERE c.id = :jobId
      """)
  void recordResume(UUID jobId, ZonedDateTime now);
}
