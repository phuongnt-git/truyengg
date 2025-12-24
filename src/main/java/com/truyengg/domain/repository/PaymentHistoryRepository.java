package com.truyengg.domain.repository;

import com.truyengg.domain.entity.PaymentHistory;
import com.truyengg.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, Long> {

  Page<PaymentHistory> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

  List<PaymentHistory> findByUserAndPaymentType(User user, String paymentType);

  @Query("SELECT SUM(p.amount) FROM PaymentHistory p WHERE p.user = :user")
  Long getTotalPaymentAmountByUser(User user);
}
