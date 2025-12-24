package com.truyengg.domain.repository;

import com.truyengg.domain.entity.ComicCrawl;
import com.truyengg.domain.enums.ComicCrawlStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComicCrawlRepository extends JpaRepository<ComicCrawl, UUID>, JpaSpecificationExecutor<ComicCrawl> {

  List<ComicCrawl> findTop10ByOrderByCreatedAtDesc();

  @Query("SELECT cj FROM ComicCrawl cj WHERE cj.status = :status")
  List<ComicCrawl> findByStatus(ComicCrawlStatus status);

  @Query("SELECT cj FROM ComicCrawl cj WHERE cj.deletedAt IS NULL")
  List<ComicCrawl> findByDeletedAtIsNull();

  @Query("SELECT cj FROM ComicCrawl cj WHERE cj.status = :status AND cj.deletedAt IS NULL")
  List<ComicCrawl> findByStatusAndDeletedAtIsNull(ComicCrawlStatus status);

  @Query("SELECT COUNT(cj) FROM ComicCrawl cj WHERE cj.status = :status AND cj.deletedAt IS NULL")
  long countByStatusAndDeletedAtIsNull(ComicCrawlStatus status);

  @Query("SELECT COUNT(cj) FROM ComicCrawl cj WHERE cj.status = :status AND cj.createdBy.id = :createdBy AND cj.deletedAt IS NULL")
  long countByStatusAndCreatedByAndDeletedAtIsNull(ComicCrawlStatus status, Long createdBy);

  @Query("SELECT cj FROM ComicCrawl cj WHERE cj.status = :status ORDER BY cj.createdAt ASC")
  List<ComicCrawl> findByStatusOrderByCreatedAtAsc(ComicCrawlStatus status);
}

