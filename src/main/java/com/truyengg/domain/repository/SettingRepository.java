package com.truyengg.domain.repository;

import com.truyengg.domain.entity.Setting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SettingRepository extends JpaRepository<Setting, Long>, JpaSpecificationExecutor<Setting> {

  Optional<Setting> findByFullKey(String fullKey);

  @EntityGraph(attributePaths = {"category"})
  Optional<Setting> findWithCategoryByFullKey(String fullKey);

  @EntityGraph(attributePaths = {"category"})
  Optional<Setting> findWithCategoryById(Long id);

  @EntityGraph(attributePaths = {"category"})
  Page<Setting> findByCategoryId(Integer categoryId, Pageable pageable);

  @EntityGraph(attributePaths = {"category"})
  @NonNull
  Page<Setting> findAll(@NonNull Pageable pageable);

  @Query("SELECT COUNT(s) FROM Setting s WHERE s.categoryId = :categoryId")
  int countByCategoryId(Integer categoryId);

  @Query(value = """
        SELECT s.* FROM settings s
        WHERE
          similarity(LOWER(s.full_key), LOWER(:search)) > 0.3
          OR similarity(LOWER(s.description), LOWER(:search)) > 0.3
          OR LOWER(s.full_key) LIKE LOWER(CONCAT('%', :search, '%'))
          OR LOWER(s.description) LIKE LOWER(CONCAT('%', :search, '%'))
        ORDER BY
          GREATEST(
            similarity(LOWER(s.full_key), LOWER(:search)),
            similarity(LOWER(s.description), LOWER(:search))
          ) DESC,
          s.full_key
        LIMIT 50
      """, nativeQuery = true)
  List<Setting> fuzzySearch(String search);

  @Query(value = """
        SELECT s.* FROM settings s
        WHERE
          similarity(LOWER(s.full_key), LOWER(:search)) > 0.3
          OR similarity(LOWER(s.description), LOWER(:search)) > 0.3
          OR LOWER(s.full_key) LIKE LOWER(CONCAT('%', :search, '%'))
          OR LOWER(s.description) LIKE LOWER(CONCAT('%', :search, '%'))
        ORDER BY
          GREATEST(
            similarity(LOWER(s.full_key), LOWER(:search)),
            similarity(LOWER(s.description), LOWER(:search))
          ) DESC,
          s.full_key
      """,
      countQuery = """
            SELECT COUNT(*) FROM settings s
            WHERE
              similarity(LOWER(s.full_key), LOWER(:search)) > 0.3
              OR similarity(LOWER(s.description), LOWER(:search)) > 0.3
              OR LOWER(s.full_key) LIKE LOWER(CONCAT('%', :search, '%'))
              OR LOWER(s.description) LIKE LOWER(CONCAT('%', :search, '%'))
          """,
      nativeQuery = true)
  Page<Setting> fuzzySearchPaginated(String search, Pageable pageable);
}

