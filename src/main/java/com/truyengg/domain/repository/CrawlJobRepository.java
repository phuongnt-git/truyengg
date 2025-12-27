package com.truyengg.domain.repository;

import com.truyengg.domain.entity.CrawlJob;
import com.truyengg.domain.enums.CrawlStatus;
import com.truyengg.domain.enums.CrawlType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CrawlJobRepository extends JpaRepository<CrawlJob, UUID>, JpaSpecificationExecutor<CrawlJob> {

  List<CrawlJob> findByStatusIn(Collection<CrawlStatus> statuses);

  long countByStatusIn(Collection<CrawlStatus> statuses);

  @Query("SELECT j FROM CrawlJob j WHERE j.crawlType = :crawlType AND j.parentJob IS NULL")
  Page<CrawlJob> findRootJobsByType(CrawlType crawlType, Pageable pageable);

  List<CrawlJob> findByParentJobId(UUID parentJobId);

  boolean existsByParentJobId(UUID parentJobId);

  long countByParentJobId(UUID parentJobId);

  List<CrawlJob> findByRootJobId(UUID rootJobId);

  @Query("SELECT j FROM CrawlJob j WHERE j.parentJob IS NULL ORDER BY j.createdAt DESC")
  Page<CrawlJob> findAllRootJobs(Pageable pageable);

  Optional<CrawlJob> findFirstByTargetUrlAndStatusInOrderByCreatedAtDesc(
      String targetUrl, Collection<CrawlStatus> statuses);

  Optional<CrawlJob> findFirstByContentIdAndStatusInOrderByCreatedAtDesc(
      long contentId, Collection<CrawlStatus> statuses);

  @Query("SELECT COUNT(j) FROM CrawlJob j WHERE j.createdBy.id = :userId AND j.status IN :statuses")
  long countByCreatedByIdAndStatusIn(Long userId, Collection<CrawlStatus> statuses);

  @Query("""
      SELECT j.crawlType, j.status, COUNT(j)
      FROM CrawlJob j
      GROUP BY j.crawlType, j.status
      """)
  List<Object[]> countByTypeAndStatus();

  @Modifying
  @Query("UPDATE CrawlJob j SET j.deletedAt = :now WHERE j.id IN :ids")
  void softDeleteByIds(Collection<UUID> ids, ZonedDateTime now);

  @Modifying
  @Query("UPDATE CrawlJob j SET j.deletedAt = NULL WHERE j.id IN :ids")
  void restoreByIds(Collection<UUID> ids);

  @Query(value = "SELECT * FROM crawl_jobs WHERE id = :id", nativeQuery = true)
  Optional<CrawlJob> findByIdIncludeDeleted(UUID id);

  @Query(value = """
      SELECT * FROM crawl_jobs
      WHERE deleted_at IS NOT NULL
      AND deleted_at < :cutoff
      """, nativeQuery = true)
  List<CrawlJob> findSoftDeletedBefore(ZonedDateTime cutoff);

  @Modifying
  @Query("UPDATE CrawlJob j SET j.status = :status, j.updatedAt = :now WHERE j.id IN :ids")
  void updateStatusByIds(
      Collection<UUID> ids,
      CrawlStatus status,
      ZonedDateTime now);
}

