package com.truyengg.model.dto;

import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.EMPTY;

/**
 * Result of setting value validation against constraints
 */
public record ValidatedSettingResult(
    boolean isValid,
    List<String> errors,
    String currentLength,
    String constraintInfo
) {

  public static ValidatedSettingResult success() {
    return new ValidatedSettingResult(true, emptyList(), EMPTY, EMPTY);
  }

  public static ValidatedSettingResult success(String currentLength, String constraintInfo) {
    return new ValidatedSettingResult(true, emptyList(), currentLength, constraintInfo);
  }

  public static ValidatedSettingResult failure(String error) {
    return new ValidatedSettingResult(false, singletonList(error), EMPTY, EMPTY);
  }

  public static ValidatedSettingResult failure(List<String> errors, String currentLength, String constraintInfo) {
    return new ValidatedSettingResult(false, errors, currentLength, constraintInfo);
  }
}

