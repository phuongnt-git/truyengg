package com.truyengg.domain.repository;

import com.truyengg.domain.entity.SettingCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SettingCategoryRepository extends JpaRepository<SettingCategory, Integer> {

  Optional<SettingCategory> findByPath(String path);

  Optional<SettingCategory> findByCode(String code);

  @Query("SELECT c FROM SettingCategory c WHERE c.parentId IS NULL ORDER BY c.name")
  List<SettingCategory> findRootCategories();

  List<SettingCategory> findByParentId(Integer parentId);

  @Query("SELECT c FROM SettingCategory c WHERE c.path LIKE CONCAT(:pathPrefix, '%') ORDER BY c.path")
  List<SettingCategory> findByPathPrefix(String pathPrefix);

  @Query("SELECT c FROM SettingCategory c WHERE c.isActive = true ORDER BY c.path")
  List<SettingCategory> findAllActive();
}

