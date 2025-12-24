package com.truyengg.domain.repository;

import com.truyengg.domain.entity.CategoryCrawlJob;
import com.truyengg.domain.enums.CategoryCrawlJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryCrawlJobRepository extends JpaRepository<CategoryCrawlJob, UUID>, JpaSpecificationExecutor<CategoryCrawlJob> {

  List<CategoryCrawlJob> findByStatus(CategoryCrawlJobStatus status);

  List<CategoryCrawlJob> findByCreatedBy_Id(Long userId);

  List<CategoryCrawlJob> findByStatusOrderByCreatedAtAsc(CategoryCrawlJobStatus status);
}

