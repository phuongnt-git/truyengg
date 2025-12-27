package com.truyengg.domain.enums;

import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isEmpty;

public enum CrawlSourceType {
  HTML, API;

  private static final Pattern MIMI_HENTAI_PATTERN = Pattern.compile(".*mimihentai\\.com.*/g/\\d+.*");

  public static CrawlSourceType detectSourceFromUrl(String url) {
    if (isEmpty(url)) {
      return HTML;
    }
    if (MIMI_HENTAI_PATTERN.matcher(url).matches()) {
      return API;
    }
    return HTML;
  }
}

