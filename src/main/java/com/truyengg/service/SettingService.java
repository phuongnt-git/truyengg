package com.truyengg.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.truyengg.domain.entity.Setting;
import com.truyengg.domain.entity.SettingCategory;
import com.truyengg.domain.repository.SettingCategoryRepository;
import com.truyengg.domain.repository.SettingRepository;
import com.truyengg.domain.exception.ResourceNotFoundException;
import com.truyengg.model.dto.SettingCategoryNode;
import com.truyengg.model.dto.SettingCategoryResponse;
import com.truyengg.model.dto.SettingDetail;
import com.truyengg.model.dto.SettingListItem;
import com.truyengg.model.dto.ValidatedSettingResult;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static com.truyengg.model.dto.ValidatedSettingResult.failure;
import static com.truyengg.model.dto.ValidatedSettingResult.success;
import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SettingService {

  SettingRepository settingRepository;
  SettingCategoryRepository categoryRepository;
  ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  @Cacheable(value = "cfg:settings#10m", key = "#fullKey")
  public Optional<Setting> getSetting(String fullKey) {
    return settingRepository.findByFullKey(fullKey);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "cfg:settings#10m", key = "'str:' + #fullKey")
  public String getStringValue(String fullKey, String defaultValue) {
    return getSetting(fullKey).map(Setting::getValue).filter(v -> !v.isBlank()).orElse(defaultValue);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "cfg:settings#10m", key = "'int:' + #fullKey")
  public int getIntValue(String fullKey, int defaultValue) {
    return getSetting(fullKey).map(Setting::getValue).map(v -> tryParseInt(v, fullKey, defaultValue)).orElse(defaultValue);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "cfg:settings#10m", key = "'long:' + #fullKey")
  public long getLongValue(String fullKey, long defaultValue) {
    return getSetting(fullKey).map(Setting::getValue).map(v -> tryParseLong(v, fullKey, defaultValue)).orElse(defaultValue);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "cfg:settings#10m", key = "'bool:' + #fullKey")
  public boolean getBooleanValue(String fullKey, boolean defaultValue) {
    return getSetting(fullKey).map(Setting::getValue).map(Boolean::parseBoolean).orElse(defaultValue);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "cfg:settings#10m", key = "'dbl:' + #fullKey")
  public double getDoubleValue(String fullKey, double defaultValue) {
    return getSetting(fullKey).map(Setting::getValue).map(v -> tryParseDouble(v, fullKey, defaultValue)).orElse(defaultValue);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "cfg:settings#10m", key = "'val:' + #fullKey")
  public String getSettingValue(String fullKey, String defaultValue) {
    return getStringValue(fullKey, defaultValue);
  }

  @Transactional
  @CacheEvict(value = "cfg:settings#10m", allEntries = true)
  public void saveSetting(String fullKey, String value, String description) {
    var setting = settingRepository.findByFullKey(fullKey).orElseThrow(() -> new ResourceNotFoundException("Setting not found: " + fullKey));

    setting.setValue(value);
    if (isNotBlank(description)) {
      setting.setDescription(description);
    }

    settingRepository.save(setting);
  }

  @Transactional(readOnly = true)
  public List<Setting> fuzzySearch(String search) {
    if (isBlank(search)) {
      return settingRepository.findAll();
    }
    return settingRepository.fuzzySearch(search);
  }

  @Transactional(readOnly = true)
  public Page<SettingListItem> searchSettingsPaginated(String search, Pageable pageable) {
    if (isBlank(search)) {
      return settingRepository.findAll(pageable).map(SettingListItem::from);
    }
    return settingRepository.fuzzySearchPaginated(search, pageable).map(SettingListItem::from);
  }

  @Transactional(readOnly = true)
  public SettingDetail getSettingById(Long id) {
    var setting = settingRepository.findWithCategoryById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Setting not found: " + id));
    return SettingDetail.from(setting);
  }

  @Transactional(readOnly = true)
  public SettingDetail getSettingByFullKey(String fullKey) {
    var setting = settingRepository.findWithCategoryByFullKey(fullKey)
        .orElseThrow(() -> new ResourceNotFoundException("Setting not found: " + fullKey));
    return SettingDetail.from(setting);
  }

  @Transactional(readOnly = true)
  public Page<SettingListItem> getSettingsPaginated(Pageable pageable) {
    return settingRepository.findAll(pageable).map(SettingListItem::from);
  }

  @Transactional(readOnly = true)
  public Page<SettingListItem> getSettingsByCategoryPaginated(Integer categoryId, Pageable pageable) {
    return settingRepository.findByCategoryId(categoryId, pageable).map(SettingListItem::from);
  }

  @Transactional(readOnly = true)
  public List<SettingCategory> getAllCategories() {
    return categoryRepository.findAll();
  }

  @Transactional(readOnly = true)
  public SettingCategoryResponse getCategory() {
    var allCategories = categoryRepository.findAll();
    var childrenMap = new HashMap<Integer, List<SettingCategory>>();
    var settingCountMap = new HashMap<Integer, Integer>();

    for (var cat : allCategories) {
      childrenMap.computeIfAbsent(cat.getParentId(), k -> new ArrayList<>()).add(cat);
      settingCountMap.put(cat.getId(), settingRepository.countByCategoryId(cat.getId()));
    }

    var roots = new ArrayList<SettingCategoryNode>();
    for (var cat : allCategories) {
      if (cat.getParentId() == null) {
        roots.add(buildCategoryTreeNode(cat, childrenMap, settingCountMap));
      }
    }

    roots.sort(comparing(SettingCategoryNode::name));

    var totalCategories = allCategories.size();
    var totalSettings = settingRepository.count();

    return new SettingCategoryResponse(roots, totalCategories, totalSettings);
  }

  private SettingCategoryNode buildCategoryTreeNode(SettingCategory category, HashMap<Integer, List<SettingCategory>> childrenMap, HashMap<Integer, Integer> settingCountMap) {
    var children = childrenMap.getOrDefault(category.getId(), emptyList());
    var childNodes = children.stream()
        .map(child -> buildCategoryTreeNode(child, childrenMap, settingCountMap))
        .sorted(comparing(SettingCategoryNode::name))
        .toList();

    var settingCount = settingCountMap.getOrDefault(category.getId(), 0);

    return SettingCategoryNode.from(category, settingCount, new ArrayList<>(childNodes));
  }

  public ValidatedSettingResult validateConstraints(String fullKey, String value) {
    var settingOpt = settingRepository.findByFullKey(fullKey);
    if (settingOpt.isEmpty()) {
      return failure("Setting not found: " + fullKey);
    }

    var setting = settingOpt.get();
    var constraints = setting.getConstraints();
    var errors = new ArrayList<String>();

    if (constraints == null || constraints.isEmpty()) {
      return success();
    }

    var currentLength = EMPTY;
    var constraintInfo = EMPTY;

    switch (setting.getValueType()) {
      case INT, LONG -> {
        try {
          var num = parseLong(value);
          var min = constraints.containsKey("min") ? ((Number) constraints.get("min")).longValue() : null;
          var max = constraints.containsKey("max") ? ((Number) constraints.get("max")).longValue() : null;

          if (min != null && num < min) {
            errors.add("Value must be >= " + min);
          }
          if (max != null && num > max) {
            errors.add("Value must be <= " + max);
          }

          if (min != null && max != null) {
            constraintInfo = "Range: " + min + " - " + max;
          } else if (min != null) {
            constraintInfo = "Min: " + min;
          } else if (max != null) {
            constraintInfo = "Max: " + max;
          }
        } catch (NumberFormatException e) {
          errors.add("Invalid number format");
        }
      }
      case DOUBLE -> {
        try {
          var num = parseDouble(value);
          var min = constraints.containsKey("min") ? ((Number) constraints.get("min")).doubleValue() : null;
          var max = constraints.containsKey("max") ? ((Number) constraints.get("max")).doubleValue() : null;

          if (min != null && num < min) {
            errors.add("Value must be >= " + min);
          }
          if (max != null && num > max) {
            errors.add("Value must be <= " + max);
          }

          if (min != null && max != null) {
            constraintInfo = "Range: " + min + " - " + max;
          }
        } catch (NumberFormatException e) {
          errors.add("Invalid decimal format");
        }
      }
      case STRING, PASSWORD, SECRET -> {
        var minLength = constraints.containsKey("minLength") ? ((Number) constraints.get("minLength")).intValue() : null;

        currentLength = value.length() + " characters";

        if (minLength != null) {
          constraintInfo = "Min length: " + minLength;
          if (value.length() < minLength) {
            errors.add("Minimum length: " + minLength + " characters");
          }
        }

        if (constraints.containsKey("allowedValues") && constraints.get("allowedValues") instanceof List<?> allowed) {
          constraintInfo = "Allowed: " + allowed;
          if (!allowed.contains(value)) {
            errors.add("Value must be one of: " + allowed);
          }
        }
      }
      case BOOLEAN -> {
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
          errors.add("Value must be 'true' or 'false'");
        }
      }
      case URL -> {
        if (!value.matches("^https?://.*")) {
          errors.add("Invalid URL format");
        }
      }
      case EMAIL -> {
        if (!value.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
          errors.add("Invalid email format");
        }
      }
      case JSON -> {
        try {
          objectMapper.readTree(value);
        } catch (Exception e) {
          errors.add("Invalid JSON format: " + e.getMessage());
        }
      }
    }

    if (errors.isEmpty()) {
      return success(currentLength, constraintInfo);
    }

    return failure(errors, currentLength, constraintInfo);
  }

  @Transactional
  @CacheEvict(value = "cfg:settings#10m", allEntries = true)
  public Setting updateSetting(String fullKey, String value, Long userId) {
    var setting = settingRepository.findByFullKey(fullKey).orElseThrow(() -> new ResourceNotFoundException("Setting not found: " + fullKey));

    if (setting.isReadonly()) {
      throw new IllegalStateException("Setting is readonly: " + fullKey);
    }

    validateValue(setting, value);

    setting.setValue(value);
    setting.setUpdatedBy(userId);

    return settingRepository.save(setting);
  }

  @Transactional
  @CacheEvict(value = "cfg:settings#10m", allEntries = true)
  public void deleteSetting(String fullKey) {
    settingRepository.findByFullKey(fullKey).ifPresent(settingRepository::delete);
  }

  private void validateValue(Setting setting, String value) {
    var constraints = setting.getConstraints();
    if (constraints == null) return;

    switch (setting.getValueType()) {
      case INT, LONG -> {
        var num = parseLong(value);
        if (constraints.containsKey("min") && num < ((Number) constraints.get("min")).longValue()) {
          throw new IllegalStateException("Value must be >= " + constraints.get("min"));
        }
        if (constraints.containsKey("max") && num > ((Number) constraints.get("max")).longValue()) {
          throw new IllegalStateException("Value must be <= " + constraints.get("max"));
        }
      }
      case DOUBLE -> {
        var num = parseDouble(value);
        if (constraints.containsKey("min") && num < ((Number) constraints.get("min")).doubleValue()) {
          throw new IllegalStateException("Value must be >= " + constraints.get("min"));
        }
        if (constraints.containsKey("max") && num > ((Number) constraints.get("max")).doubleValue()) {
          throw new IllegalStateException("Value must be <= " + constraints.get("max"));
        }
      }
      case STRING, PASSWORD, SECRET -> {
        if (constraints.containsKey("minLength") && value.length() < ((Number) constraints.get("minLength")).intValue()) {
          throw new IllegalStateException("Minimum length: " + constraints.get("minLength"));
        }
        if (constraints.containsKey("allowedValues")) {
          if (constraints.get("allowedValues") instanceof List<?> allowed) {
            if (!allowed.contains(value)) {
              throw new IllegalStateException("Allowed values: " + allowed);
            }
          }
        }
      }
      case BOOLEAN -> {
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
          throw new IllegalStateException("Value must be 'true' or 'false'");
        }
      }
      case URL -> {
        if (!value.matches("^https?://.*")) {
          throw new IllegalStateException("Invalid URL format");
        }
      }
      case EMAIL -> {
        if (!value.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
          throw new IllegalStateException("Invalid email format");
        }
      }
      case JSON -> {
        try {
          objectMapper.readTree(value);
        } catch (Exception e) {
          throw new IllegalStateException("Invalid JSON format: " + e.getMessage());
        }
      }
    }
  }

  private int tryParseInt(String v, String key, int def) {
    try {
      return parseInt(v);
    } catch (Exception e) {
      log.warn("Invalid int for {}: {}", key, v);
      return def;
    }
  }

  private long tryParseLong(String v, String key, long def) {
    try {
      return parseLong(v);
    } catch (Exception e) {
      log.warn("Invalid long for {}: {}", key, v);
      return def;
    }
  }

  private double tryParseDouble(String v, String key, double def) {
    try {
      return parseDouble(v);
    } catch (Exception e) {
      log.warn("Invalid double for {}: {}", key, v);
      return def;
    }
  }
}

