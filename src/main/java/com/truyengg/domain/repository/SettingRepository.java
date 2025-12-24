package com.truyengg.domain.repository;

import com.truyengg.domain.entity.Setting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SettingRepository extends JpaRepository<Setting, Long>, JpaSpecificationExecutor<Setting> {

  Optional<Setting> findBySettingKey(String settingKey);
}

