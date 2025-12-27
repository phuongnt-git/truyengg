package com.truyengg.domain.enums;

import lombok.Getter;

@Getter
public enum AgeRating {
  ALL("ALL"),
  THIRTEEN_PLUS("13+"),
  SIXTEEN_PLUS("16+"),
  EIGHTEEN_PLUS("18+"),
  MATURE("MATURE");

  private final String value;

  AgeRating(String value) {
    this.value = value;
  }

  public static AgeRating fromString(String value) {
    if (value == null) {
      return ALL;
    }
    for (AgeRating rating : AgeRating.values()) {
      if (rating.value.equalsIgnoreCase(value)) {
        return rating;
      }
    }
    return ALL;
  }
}

