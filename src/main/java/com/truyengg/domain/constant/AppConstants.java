package com.truyengg.domain.constant;

import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.regex.Pattern;

@UtilityClass
public final class AppConstants {

  // Delay constants
  public static final int DATA_BUFFER_MAX_SIZE_BYTES = 10 * 1024 * 1024;

  public static final String MSG_FOUND = "Found ";

  // HTML attributes
  public static final String ATTR_DATA_ORIGINAL = "data-original";
  public static final String ATTR_DATA_SRC = "data-src";
  public static final String ATTR_SRC = "src";
  public static final String PREFIX_DATA_URI = "data:";

  // Protocols
  public static final String PROTOCOL_HTTP = "http://";
  public static final String PROTOCOL_HTTPS = "https://";

  // URLs
  public static final String API_BASE_URL = "https://mimihentai.com";
  public static final String MIMI_HENTAI_API_GALLERY = "/api/v1/manga/gallery/";
  public static final String API_CHAPTER = "/api/v1/manga/chapter";
  public static final String DEFAULT_DOMAIN = "https://truyenqqgo.com";

  // API keys
  public static final String API_KEY_RESPONSE = "response";
  public static final String API_KEY_PAGES = "pages";
  public static final String API_KEY_ID = "id";

  // User agents - Latest Chrome/Firefox versions (Dec 2024)
  public static final List<String> USER_AGENTS = List.of(
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0",
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:133.0) Gecko/20100101 Firefox/133.0",
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0",
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Safari/605.1.15",
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
  );

  public static final String VALUE_CACHE_CONTROL = "public, max-age=3600";
  public static final String FORMAT_WEBP = "webp";
  public static final String FORMAT_JPEG = "jpeg";
  public static final String CONTENT_TYPE_WEBP = "image/webp";
  public static final String CONTENT_TYPE_JPEG = "image/jpeg";
  public static final String CONTENT_TYPE_PNG = "image/png";
  public static final int GRAYSCALE_SAMPLE_SIZE = 100;
  public static final int GRAYSCALE_THRESHOLD = 10;
  public static final String BYTES = "bytes";
  public static final Pattern COMIC_URL_PATTERN = Pattern.compile("/truyen-tranh/[^/]+/?$");

  public static final String DASHBOARD_STATS = "dashboardStats";
  public static final String JOB_COUNTS = "jobCounts";
  public static final String AGGREGATED_STATS = "aggregatedStats";
  public static final String COMPLETED_JOBS = "completedJobs";
  public static final String JOB_SETTINGS = "jobSettings";
  public static final String REGISTRATION_CACHE = "passkeyRegistration";
  public static final String AUTHENTICATION_CACHE = "passkeyAuthentication";
  public static final String ASIA_HO_CHI_MINH = "Asia/Ho_Chi_Minh";
}

