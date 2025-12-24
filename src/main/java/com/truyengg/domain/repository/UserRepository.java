package com.truyengg.domain.repository;

import com.truyengg.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
  Optional<User> findByEmail(String email);

  Optional<User> findByResetToken(String resetToken);

  boolean existsByEmail(String email);

  boolean existsByUsername(String username);
}
