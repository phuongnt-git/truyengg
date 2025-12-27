package com.truyengg.domain.repository;

import com.truyengg.domain.entity.Comic;
import com.truyengg.domain.entity.User;
import com.truyengg.domain.entity.UserFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserFollowRepository extends JpaRepository<UserFollow, Long>, JpaSpecificationExecutor<UserFollow> {
  Optional<UserFollow> findByUserAndComic(User user, Comic comic);

  boolean existsByUserAndComic(User user, Comic comic);

  List<UserFollow> findByUserOrderByCreatedAtDesc(User user);

  @Query("SELECT COUNT(uf) FROM UserFollow uf WHERE uf.comic = :comic")
  long countByComic(Comic comic);
}

