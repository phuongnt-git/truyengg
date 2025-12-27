package com.truyengg.service;

import com.truyengg.domain.entity.Setting;
import com.truyengg.domain.entity.SettingCategory;
import com.truyengg.domain.enums.SettingValueType;
import com.truyengg.domain.repository.SettingCategoryRepository;
import com.truyengg.domain.repository.SettingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class SettingServiceTest {

  @Autowired
  private SettingService settingService;

  @Autowired
  private SettingRepository settingRepository;

  @Autowired
  private SettingCategoryRepository categoryRepository;

  @Test
  void shouldLoadSettingFromDatabase() {
    // Given
    var category = categoryRepository.findByPath("security.jwt.tokens").orElseThrow();
    var setting = Setting.builder()
      .categoryId(category.getId())
      .key("test_key")
      .value("test_value")
      .valueType(SettingValueType.STRING)
      .build();
    settingRepository.save(setting);

    // When
    var result = settingService.getSetting("security.jwt.tokens.test_key");

    // Then
    assertTrue(result.isPresent());
    assertEquals("test_value", result.get().getValue());
  }

  @Test
  void shouldFuzzySearchSettings() {
    // When - search with typo
    var results = settingService.fuzzySearch("tokn");

    // Then - should still find "tokens" settings
    assertFalse(results.isEmpty());
    assertTrue(results.stream().anyMatch(s -> s.getFullKey() != null && s.getFullKey().contains("token")));
  }

  @Test
  void shouldGetCategoryTree() {
    // When
    var tree = settingService.getCategoryTree();

    // Then
    assertNotNull(tree);
    assertFalse(tree.isEmpty());
    assertTrue(tree.stream().anyMatch(node -> "security".equals(node.getCategory().getCode())));
  }

  @Test
  void shouldValidateIntegerConstraints() {
    // Given
    var category = categoryRepository.findByPath("features.crawl").orElseThrow();
    
    // When/Then - should validate constraints
    assertDoesNotThrow(() -> {
      settingService.updateSetting("features.crawl.max_retries", "5", 1L);
    });
  }
}

