package com.truyengg.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.truyengg.domain.entity.Setting;
import com.truyengg.domain.entity.SettingCategory;
import com.truyengg.domain.exception.ResourceNotFoundException;
import com.truyengg.domain.repository.SettingCategoryRepository;
import com.truyengg.domain.repository.SettingRepository;
import com.truyengg.model.dto.SettingCategoryNode;
import com.truyengg.model.dto.SettingCategoryResponse;
import com.truyengg.model.dto.SettingListItem;
import com.truyengg.model.dto.SettingResponse;
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
import java.util.Map;

import static com.truyengg.model.dto.SettingResponse.from;
import static com.truyengg.model.dto.ValidatedSettingResult.failure;
import static com.truyengg.model.dto.ValidatedSettingResult.success;
import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SettingService {

  private static final String SETTING_NOT_FOUND = "Setting not found: ";
  private static final String CONSTRAINT_MIN_LENGTH = "minLength";
  private static final String CONSTRAINT_ALLOWED_VALUES = "allowedValues";
  private static final String CONSTRAINT_MIN = "min";
  private static final String CONSTRAINT_MAX = "max";

  SettingRepository settingRepository;
  SettingCategoryRepository categoryRepository;
  ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  @Cacheable(value = "cfg:settings#10m", key = "'str:' + #fullKey")
  public String getStringValue(String fullKey, String defaultValue) {
    return settingRepository.findByFullKey(fullKey)
        .map(Setting::getValue)
        .filter(v -> !v.isBlank())
        .orElse(defaultValue);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "cfg:settings#10m", key = "'int:' + #fullKey")
  public int getIntValue(String fullKey, int defaultValue) {
    return settingRepository.findByFullKey(fullKey)
        .map(Setting::getValue)
        .map(v -> tryParseInt(v, fullKey, defaultValue))
        .orElse(defaultValue);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "cfg:settings#10m", key = "'long:' + #fullKey")
  public long getLongValue(String fullKey, long defaultValue) {
    return settingRepository.findByFullKey(fullKey)
        .map(Setting::getValue)
        .map(v -> tryParseLong(v, fullKey, defaultValue))
        .orElse(defaultValue);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "cfg:settings#10m", key = "'bool:' + #fullKey")
  public boolean getBooleanValue(String fullKey, boolean defaultValue) {
    return settingRepository.findByFullKey(fullKey)
        .map(Setting::getValue)
        .map(Boolean::parseBoolean)
        .orElse(defaultValue);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "cfg:settings#10m", key = "'dbl:' + #fullKey")
  public double getDoubleValue(String fullKey, double defaultValue) {
    return settingRepository.findByFullKey(fullKey)
        .map(Setting::getValue)
        .map(v -> tryParseDouble(v, fullKey, defaultValue))
        .orElse(defaultValue);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "cfg:settings#10m", key = "'val:' + #fullKey")
  public String getSettingValue(String fullKey, String defaultValue) {
    return settingRepository.findByFullKey(fullKey)
        .map(Setting::getValue)
        .filter(v -> !v.isBlank())
        .orElse(defaultValue);
  }

  public List<Setting> fuzzySearch(String search) {
    if (isBlank(search)) {
      return settingRepository.findAll();
    }
    return settingRepository.fuzzySearch(search);
  }

  public Page<SettingListItem> searchSettingsPaginated(String search, Pageable pageable) {
    if (isBlank(search)) {
      return settingRepository.findAll(pageable).map(SettingListItem::from);
    }
    return settingRepository.fuzzySearchPaginated(search, pageable).map(SettingListItem::from);
  }

  public SettingResponse getSettingById(Long id) {
    var setting = settingRepository.findWithCategoryById(id)
        .orElseThrow(() -> new ResourceNotFoundException(SETTING_NOT_FOUND + id));
    return from(setting);
  }

  public SettingResponse getSettingByFullKey(String fullKey) {
    var setting = settingRepository.findWithCategoryByFullKey(fullKey)
        .orElseThrow(() -> new ResourceNotFoundException(SETTING_NOT_FOUND + fullKey));
    return from(setting);
  }

  public Page<SettingListItem> getSettingsPaginated(Pageable pageable) {
    return settingRepository.findAll(pageable).map(SettingListItem::from);
  }

  public Page<SettingListItem> getSettingsByCategoryPaginated(Integer categoryId, Pageable pageable) {
    return settingRepository.findByCategoryId(categoryId, pageable).map(SettingListItem::from);
  }

  @Transactional(readOnly = true)
  public SettingCategoryResponse getCategoryTree() {
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

  private SettingCategoryNode buildCategoryTreeNode(
      SettingCategory category,
      HashMap<Integer, List<SettingCategory>> childrenMap,
      HashMap<Integer, Integer> settingCountMap
  ) {
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
      return failure(SETTING_NOT_FOUND + fullKey);
    }

    var setting = settingOpt.get();
    var constraints = setting.getConstraints();

    if (constraints == null || constraints.isEmpty()) {
      return success();
    }

    return validateByValueType(setting, value, constraints);
  }

  private ValidatedSettingResult validateByValueType(Setting setting, String value, Map<String, Object> constraints) {
    return switch (setting.getValueType()) {
      case INT, LONG -> validateNumericConstraints(value, constraints);
      case DOUBLE -> validateDoubleConstraints(value, constraints);
      case STRING, PASSWORD, SECRET -> validateStringConstraints(value, constraints);
      case BOOLEAN -> validateBooleanConstraints(value);
      case URL -> validateUrlConstraints(value);
      case EMAIL -> validateEmailConstraints(value);
      case JSON -> validateJsonConstraints(value);
    };
  }

  private ValidatedSettingResult validateNumericConstraints(String value, Map<String, Object> constraints) {
    var errors = new ArrayList<String>();
    var constraintInfo = EMPTY;

    try {
      var num = parseLong(value);
      var min = getConstraintAsLong(constraints, CONSTRAINT_MIN);
      var max = getConstraintAsLong(constraints, CONSTRAINT_MAX);

      if (min != null && num < min) {
        errors.add("Value must be >= " + min);
      }
      if (max != null && num > max) {
        errors.add("Value must be <= " + max);
      }

      constraintInfo = buildRangeInfo(min, max);
    } catch (NumberFormatException e) {
      errors.add("Invalid number format");
    }

    return errors.isEmpty() ? success(EMPTY, constraintInfo) : failure(errors, EMPTY, constraintInfo);
  }

  private ValidatedSettingResult validateDoubleConstraints(String value, Map<String, Object> constraints) {
    var errors = new ArrayList<String>();
    var constraintInfo = EMPTY;

    try {
      var num = parseDouble(value);
      var min = getConstraintAsDouble(constraints, CONSTRAINT_MIN);
      var max = getConstraintAsDouble(constraints, CONSTRAINT_MAX);

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

    return errors.isEmpty() ? success(EMPTY, constraintInfo) : failure(errors, EMPTY, constraintInfo);
  }

  private ValidatedSettingResult validateStringConstraints(String value, Map<String, Object> constraints) {
    var errors = new ArrayList<String>();
    var currentLength = value.length() + " characters";
    var constraintInfo = EMPTY;

    var minLength = getConstraintAsInt(constraints, CONSTRAINT_MIN_LENGTH);

    if (minLength != null) {
      constraintInfo = "Min length: " + minLength;
      if (value.length() < minLength) {
        errors.add("Minimum length: " + minLength + " characters");
      }
    }

    if (constraints.get(CONSTRAINT_ALLOWED_VALUES) instanceof List<?> allowed) {
      constraintInfo = "Allowed: " + allowed;
      if (!allowed.contains(value)) {
        errors.add("Value must be one of: " + allowed);
      }
    }

    return errors.isEmpty() ? success(currentLength, constraintInfo) : failure(errors, currentLength, constraintInfo);
  }

  private ValidatedSettingResult validateBooleanConstraints(String value) {
    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
      return failure("Value must be 'true' or 'false'");
    }
    return success();
  }

  private ValidatedSettingResult validateUrlConstraints(String value) {
    if (!value.matches("^https?://.*")) {
      return failure("Invalid URL format");
    }
    return success();
  }

  private ValidatedSettingResult validateEmailConstraints(String value) {
    if (!value.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
      return failure("Invalid email format");
    }
    return success();
  }

  private ValidatedSettingResult validateJsonConstraints(String value) {
    try {
      objectMapper.readTree(value);
      return success();
    } catch (Exception e) {
      return failure("Invalid JSON format: " + e.getMessage());
    }
  }

  private String buildRangeInfo(Long min, Long max) {
    if (min != null && max != null) {
      return "Range: " + min + " - " + max;
    } else if (min != null) {
      return "Min: " + min;
    } else if (max != null) {
      return "Max: " + max;
    }
    return EMPTY;
  }

  private Long getConstraintAsLong(Map<String, Object> constraints, String key) {
    return constraints.containsKey(key) ? ((Number) constraints.get(key)).longValue() : null;
  }

  private Double getConstraintAsDouble(Map<String, Object> constraints, String key) {
    return constraints.containsKey(key) ? ((Number) constraints.get(key)).doubleValue() : null;
  }

  private Integer getConstraintAsInt(Map<String, Object> constraints, String key) {
    return constraints.containsKey(key) ? ((Number) constraints.get(key)).intValue() : null;
  }

  @Transactional
  @CacheEvict(value = "cfg:settings#10m", allEntries = true)
  public Setting updateSetting(String fullKey, String value, Long userId) {
    var setting = settingRepository.findByFullKey(fullKey)
        .orElseThrow(() -> new ResourceNotFoundException(SETTING_NOT_FOUND + fullKey));

    if (setting.isReadonly()) {
      throw new IllegalStateException("Setting is readonly: " + fullKey);
    }

    validateValue(setting, value);

    setting.setValue(value);
    setting.setUpdatedBy(userId);

    return settingRepository.save(setting);
  }

  private void validateValue(Setting setting, String value) {
    var constraints = setting.getConstraints();
    if (constraints == null) return;

    switch (setting.getValueType()) {
      case INT, LONG -> validateNumericValue(value, constraints);
      case DOUBLE -> validateDoubleValue(value, constraints);
      case STRING, PASSWORD, SECRET -> validateStringValue(value, constraints);
      case BOOLEAN -> validateBooleanValue(value);
      case URL -> validateUrlValue(value);
      case EMAIL -> validateEmailValue(value);
      case JSON -> validateJsonValue(value);
    }
  }

  private void validateNumericValue(String value, Map<String, Object> constraints) {
    var num = parseLong(value);
    var min = getConstraintAsLong(constraints, CONSTRAINT_MIN);
    var max = getConstraintAsLong(constraints, CONSTRAINT_MAX);

    if (min != null && num < min) {
      throw new IllegalStateException("Value must be >= " + min);
    }
    if (max != null && num > max) {
      throw new IllegalStateException("Value must be <= " + max);
    }
  }

  private void validateDoubleValue(String value, Map<String, Object> constraints) {
    var num = parseDouble(value);
    var min = getConstraintAsDouble(constraints, CONSTRAINT_MIN);
    var max = getConstraintAsDouble(constraints, CONSTRAINT_MAX);

    if (min != null && num < min) {
      throw new IllegalStateException("Value must be >= " + min);
    }
    if (max != null && num > max) {
      throw new IllegalStateException("Value must be <= " + max);
    }
  }

  private void validateStringValue(String value, Map<String, Object> constraints) {
    var minLength = getConstraintAsInt(constraints, CONSTRAINT_MIN_LENGTH);

    if (minLength != null && value.length() < minLength) {
      throw new IllegalStateException("Minimum length: " + minLength);
    }

    if (constraints.get(CONSTRAINT_ALLOWED_VALUES) instanceof List<?> allowed && !allowed.contains(value)) {
      throw new IllegalStateException("Allowed values: " + allowed);
    }
  }

  private void validateBooleanValue(String value) {
    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
      throw new IllegalStateException("Value must be 'true' or 'false'");
    }
  }

  private void validateUrlValue(String value) {
    if (!value.matches("^https?://.*")) {
      throw new IllegalStateException("Invalid URL format");
    }
  }

  private void validateEmailValue(String value) {
    if (!value.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
      throw new IllegalStateException("Invalid email format");
    }
  }

  private void validateJsonValue(String value) {
    try {
      objectMapper.readTree(value);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid JSON format: " + e.getMessage());
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
