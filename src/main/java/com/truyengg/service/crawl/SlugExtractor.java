package com.truyengg.service.crawl;

import lombok.experimental.UtilityClass;

import java.net.URI;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@UtilityClass
public class SlugExtractor {

  private static final Pattern MIMI_PATTERN = Pattern.compile("/g/(\\d+)");
  private static final Pattern TRUYENQQ_PATTERN = Pattern.compile("/truyen-tranh/([^/]+?)(?:/chapter|/chap|/chuong|/?$)");

  /**
   * Extracts slug/comic identifier from URL.
   * Supports MimiHentai (/g/12345) and TruyenQQ (/truyen-tranh/comic-name) patterns.
   */
  public static String extractFromUrl(String url) {
    if (isBlank(url)) {
      return EMPTY;
    }

    // Try MimiHentai pattern: /g/(\d+)
    var mimiMatcher = MIMI_PATTERN.matcher(url);
    if (mimiMatcher.find()) {
      return "mimi-" + mimiMatcher.group(1);
    }

    // Try TruyenQQ pattern
    var truyenMatcher = TRUYENQQ_PATTERN.matcher(url);
    if (truyenMatcher.find()) {
      return truyenMatcher.group(1);
    }

    // Generic extraction from URL path
    return extractFromPath(url);
  }

  /**
   * Extracts comic ID from URL for storage/file operations.
   * Similar to extractFromUrl but with additional prefix for MimiHentai.
   */
  public static String extractComicId(String url) {
    return extractFromUrl(url);
  }

  /**
   * Alias for extractFromUrl.
   */
  public static String extractSlugFromUrl(String url) {
    return extractFromUrl(url);
  }

  /**
   * Alias for extractFromUrl (non-static style).
   */
  public String extract(String url) {
    return extractFromUrl(url);
  }

  /**
   * Extracts manga ID specifically from MimiHentai URL.
   * Returns the numeric ID portion only.
   */
  public static String extractMangaId(String url) {
    if (isBlank(url)) {
      return EMPTY;
    }

    var matcher = MIMI_PATTERN.matcher(url);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return EMPTY;
  }

  private static String extractFromPath(String url) {
    try {
      var uri = new URI(url);
      var path = uri.getPath();
      if (path != null && path.length() > 1) {
        var segments = path.split("/");
        for (var i = segments.length - 1; i >= 0; i--) {
          var segment = segments[i];
          if (isValidSegment(segment)) {
            return segment;
          }
        }
      }
    } catch (Exception e) {
      // Ignore URL parsing errors
    }
    return EMPTY;
  }

  private static boolean isValidSegment(String segment) {
    return isNotBlank(segment)
        && !segment.equals("truyen")
        && !segment.equals("truyen-tranh")
        && !segment.equals("g");
  }
}

