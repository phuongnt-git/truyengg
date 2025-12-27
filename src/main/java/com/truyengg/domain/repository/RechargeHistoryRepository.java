package com.truyengg.domain.repository;

import com.truyengg.domain.entity.RechargeHistory;
import com.truyengg.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RechargeHistoryRepository extends JpaRepository<RechargeHistory, Long> {

  Page<RechargeHistory> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

  List<RechargeHistory> findByUserAndStatus(User user, String status);

  @Query("SELECT SUM(r.amount) FROM RechargeHistory r WHERE r.user = :user AND r.status = 'completed'")
  Long getTotalRechargedAmountByUser(User user);
}
