package com.truyengg.service.crawl;

import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
public final class CrawlConstants {

  // Error prefix
  public static final String ERROR_PREFIX = "Error: ";

  // Delay constants
  public static final int DATA_BUFFER_MAX_SIZE_BYTES = 10 * 1024 * 1024;

  // Result map keys
  public static final String KEY_SUCCESS = "success";
  public static final String KEY_IMAGES = "images";
  public static final String KEY_MINIO_URLS = "minioUrls";
  public static final String KEY_ORIGINAL_IMAGE_URLS = "originalImageUrls";

  // Message templates
  public static final String MSG_DOWNLOADING_CHAPTERS_FROM = "Downloading chapters from ";
  public static final String MSG_DOWNLOADING_CHAPTER = "Downloading chapter ";
  public static final String MSG_FOUND = "Found ";

  // File patterns
  public static final String IMAGE_FILE_PATTERN = "image_%03d.jpg";

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

  // Search patterns
  public static final String PATTERN_CHAPTER = "chapter";
  public static final String PATTERN_CHUONG = "chuong";
  public static final String PATTERN_CHAP = "chap";
  public static final String PATTERN_NUMBERS = ".*\\d+.*";

  // API keys
  public static final String API_KEY_RESPONSE = "response";
  public static final String API_KEY_PAGES = "pages";
  public static final String API_KEY_ID = "id";

  // User agents
  public static final List<String> USER_AGENTS = List.of(
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/77.0.3865.90 Safari/537.36",
      "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/79.0.3945.130 Safari/537.36",
      "Mozilla/5.0 (iPad; CPU OS 15_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/104.0.5112.99 Mobile/15E148 Safari/604.1",
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.3",
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0",
      "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.3"
  );

  public static final String FILE_SIZE_BYTES = "fileSizeBytes";
  public static final String REQUEST_COUNT = "requestCount";
  public static final String ERROR_COUNT = "errorCount";
  public static final String ERROR = "error";
  public static final String VALUE_CACHE_CONTROL = "public, max-age=3600";
  public static final String ERROR_CRAWL_NOT_FOUND = "Crawl not found: ";
}

