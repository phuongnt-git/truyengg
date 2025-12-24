package com.truyengg.domain.repository;

import com.truyengg.domain.entity.CategoryCrawlProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryCrawlProgressRepository extends JpaRepository<CategoryCrawlProgress, UUID> {

  Optional<CategoryCrawlProgress> findByCategoryCrawlJobId(UUID categoryCrawlJobId);
}

