package com.truyengg.domain.repository;

import com.truyengg.domain.entity.CategoryCrawlDetail;
import com.truyengg.domain.enums.CategoryCrawlDetailStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryCrawlDetailRepository extends JpaRepository<CategoryCrawlDetail, Long> {

  List<CategoryCrawlDetail> findByCategoryCrawlJob_Id(UUID categoryJobId);

  long countByStatusAndCategoryCrawlJob_Id(CategoryCrawlDetailStatus status, UUID categoryJobId);

  List<CategoryCrawlDetail> findByCategoryCrawlJob_IdAndStatus(UUID categoryJobId, CategoryCrawlDetailStatus status);

  @Query("SELECT c FROM CategoryCrawlDetail c WHERE c.categoryCrawlJob.id = :jobId ORDER BY c.createdAt ASC")
  List<CategoryCrawlDetail> findByCategoryCrawlJobIdOrderByCreatedAt(UUID jobId);
}

