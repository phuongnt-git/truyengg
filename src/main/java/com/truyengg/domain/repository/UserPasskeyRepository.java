package com.truyengg.domain.repository;

import com.truyengg.domain.entity.UserPasskey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPasskeyRepository extends JpaRepository<UserPasskey, UUID> {

  List<UserPasskey> findByUserId(Long userId);

  Optional<UserPasskey> findByCredentialId(byte[] credentialId);

  @Query("SELECT p FROM UserPasskey p JOIN FETCH p.user WHERE p.credentialId = :credentialId")
  Optional<UserPasskey> findByCredentialIdWithUser(byte[] credentialId);

  boolean existsByUserIdAndDeviceName(Long userId, String deviceName);

  long countByUserId(Long userId);

  @Query("SELECT p FROM UserPasskey p WHERE p.user.email = :email")
  List<UserPasskey> findByUserEmail(String email);
}

