package com.truyengg.domain.repository;

import com.truyengg.domain.entity.Comic;
import com.truyengg.domain.entity.ReadingHistory;
import com.truyengg.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReadingHistoryRepository extends JpaRepository<ReadingHistory, Long>, JpaSpecificationExecutor<ReadingHistory> {
  Optional<ReadingHistory> findByUserAndComic(User user, Comic comic);

  List<ReadingHistory> findByUserOrderByLastReadAtDesc(User user);

  @Query("SELECT COUNT(rh) FROM ReadingHistory rh WHERE rh.user = :user")
  long countByUser(User user);
}

