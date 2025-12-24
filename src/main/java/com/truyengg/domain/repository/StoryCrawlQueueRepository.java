package com.truyengg.domain.repository;

import com.truyengg.domain.entity.StoryCrawlQueue;
import com.truyengg.domain.enums.StoryCrawlQueueStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StoryCrawlQueueRepository extends JpaRepository<StoryCrawlQueue, UUID> {

  List<StoryCrawlQueue> findByStatusOrderByCreatedAtAsc(StoryCrawlQueueStatus status, Pageable pageable);

  List<StoryCrawlQueue> findByCategoryCrawlJob_Id(UUID categoryJobId);

  long countByStatusAndCategoryCrawlJob_Id(StoryCrawlQueueStatus status, UUID categoryJobId);

  @Query("SELECT s FROM StoryCrawlQueue s WHERE s.categoryCrawlJob.id = :jobId AND s.status = :status")
  List<StoryCrawlQueue> findByCategoryJobIdAndStatus(UUID jobId, StoryCrawlQueueStatus status);
}

