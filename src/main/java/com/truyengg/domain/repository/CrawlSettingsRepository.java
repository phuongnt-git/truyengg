package com.truyengg.domain.repository;

import com.truyengg.domain.entity.CrawlSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.UUID;

@Repository
public interface CrawlSettingsRepository extends JpaRepository<CrawlSettings, UUID> {

  @Modifying
  @Query("UPDATE CrawlSettings s SET s.deletedAt = :now WHERE s.id IN :jobIds")
  void softDeleteByJobIds(Collection<UUID> jobIds, ZonedDateTime now);

  @Modifying
  @Query("UPDATE CrawlSettings s SET s.deletedAt = NULL WHERE s.id IN :jobIds")
  void restoreByJobIds(Collection<UUID> jobIds);
}
