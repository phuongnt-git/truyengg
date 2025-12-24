package com.truyengg.util;

import lombok.experimental.UtilityClass;

import java.net.URI;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;

import static org.apache.commons.lang3.StringUtils.EMPTY;

@UtilityClass
public class ImageProxyUtils {

  public static String generateETag(byte[] data) {
    try {
      var md = MessageDigest.getInstance("MD5");
      var hash = md.digest(data);
      var sb = new StringBuilder();
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return "\"" + sb + "\"";
    } catch (Exception e) {
      return "\"" + Arrays.hashCode(data) + "\"";
    }
  }

  public static Instant parseHttpDate(String httpDate) {
    try {
      // Parse HTTP date format: "Wed, 21 Oct 2015 07:28:00 GMT"
      var formatter = DateTimeFormatter.RFC_1123_DATE_TIME;
      return ZonedDateTime.parse(httpDate, formatter).toInstant();
    } catch (Exception e) {
      try {
        // Try alternative format: "Wed, 21 Oct 2015 07:28:00 +0000"
        var formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
        return ZonedDateTime.parse(httpDate, formatter).toInstant();
      } catch (Exception e2) {
        return null;
      }
    }
  }

  public static String detectContentType(String fileName) {
    var lowerName = fileName.toLowerCase();
    if (lowerName.endsWith(".webp")) {
      return "image/webp";
    } else if (lowerName.endsWith(".png")) {
      return "image/png";
    } else if (lowerName.endsWith(".gif")) {
      return "image/gif";
    }
    return "image/jpeg";
  }

  public static String extractDomain(String url) {
    try {
      var uri = new URI(url);
      return uri.getScheme() + "://" + uri.getHost();
    } catch (Exception e) {
      return EMPTY;
    }
  }
}
