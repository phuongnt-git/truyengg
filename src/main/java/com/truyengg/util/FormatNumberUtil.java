package com.truyengg.util;

import java.text.DecimalFormat;

public class FormatNumberUtil {
  private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,###");

  public static String formatNumber(long number) {
    return DECIMAL_FORMAT.format(number);
  }

  public static String formatNumber(int number) {
    return DECIMAL_FORMAT.format(number);
  }

  public static String formatCompactNumber(long number) {
    if (number < 1000) {
      return String.valueOf(number);
    } else if (number < 1_000_000) {
      return String.format("%.1fK", number / 1000.0);
    } else if (number < 1_000_000_000) {
      return String.format("%.1fM", number / 1_000_000.0);
    } else {
      return String.format("%.1fB", number / 1_000_000_000.0);
    }
  }
}

