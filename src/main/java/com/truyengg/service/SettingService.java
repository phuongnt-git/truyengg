package com.truyengg.service;

import com.truyengg.domain.entity.Setting;
import com.truyengg.domain.repository.SettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettingService {

  private final SettingRepository settingRepository;

  @Transactional(readOnly = true)
  @Cacheable(value = "settings", key = "#key", unless = "#result.isEmpty()")
  public Optional<Setting> getSetting(String key) {
    return settingRepository.findBySettingKey(key);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "settings", key = "'value:' + #key", unless = "#result == null")
  public String getSettingValue(String key, String defaultValue) {
    var value = settingRepository.findBySettingKey(key)
        .map(Setting::getSettingValue)
        .orElse(EMPTY);
    return isNotEmpty(value) ? value : defaultValue;
  }

  @Transactional
  @CacheEvict(value = "settings", allEntries = true)
  public Setting saveSetting(String key, String value, String description) {
    var setting = settingRepository.findBySettingKey(key)
        .orElse(Setting.builder()
            .settingKey(key)
            .build());

    setting.setSettingValue(value);
    if (isNotBlank(description)) {
      setting.setDescription(description);
    }

    return settingRepository.save(setting);
  }

  @Transactional
  @CacheEvict(value = "settings", allEntries = true)
  public void deleteSetting(String key) {
    settingRepository.findBySettingKey(key)
        .ifPresent(settingRepository::delete);
  }
}
