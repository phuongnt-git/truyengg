package com.truyengg.service.crawl.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.truyengg.model.dto.ChapterCrawlProcessingParams;
import com.truyengg.model.dto.ChapterInfo;
import com.truyengg.model.dto.ComicInfo;
import com.truyengg.service.comic.ComicDetectionService;
import com.truyengg.service.crawl.CrawlHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.truyengg.domain.constant.AppConstants.API_BASE_URL;
import static com.truyengg.domain.constant.AppConstants.API_CHAPTER;
import static com.truyengg.domain.constant.AppConstants.API_KEY_ID;
import static com.truyengg.domain.constant.AppConstants.API_KEY_PAGES;
import static com.truyengg.domain.constant.AppConstants.API_KEY_RESPONSE;
import static com.truyengg.domain.constant.AppConstants.MIMI_HENTAI_API_GALLERY;
import static com.truyengg.domain.constant.AppConstants.MSG_FOUND;
import static java.util.Collections.emptyList;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

@SuppressWarnings("unchecked")
@Component
@Slf4j
public class ApiCrawlHandler extends CrawlHandler {

  private final ObjectMapper objectMapper;
  private final ComicDetectionService comicDetectionService;

  public ApiCrawlHandler(ObjectMapper objectMapper,
                         ComicDetectionService comicDetectionService,
                         CrawlHttpClient crawlHttpClient) {
    super(crawlHttpClient);
    this.objectMapper = objectMapper;
    this.comicDetectionService = comicDetectionService;
  }

  @Override
  public ComicInfo detectComicInfo(String url, Document doc, Object apiResponse) {
    return comicDetectionService.detectComicInfoMimi(url, doc, apiResponse);
  }

  @Override
  public List<String> extractChapterList(String url, String domain, List<String> messages) {
    // Extract manga_id from URL
    var pattern = Pattern.compile("/g/(\\d+)");
    var matcher = pattern.matcher(url);
    if (!matcher.find()) {
      throw new IllegalArgumentException("Invalid URL. Please enter URL in format https://mimihentai.com/g/60986.");
    }

    var mangaId = matcher.group(1);
    if (messages != null) {
      messages.add(MSG_FOUND + "manga ID: " + mangaId + ".");
    }

    var headers = crawlHttpClient.buildHeaders(API_BASE_URL);

    // Try API call first
    var apiUrl = API_BASE_URL + MIMI_HENTAI_API_GALLERY + mangaId;
    var apiResponseJson = crawlHttpClient.fetchUrl(apiUrl, headers, true);

    Map<String, Object> apiResponse = null;
    if (StringUtils.isNotEmpty(apiResponseJson)) {
      try {
        apiResponse = objectMapper.readValue(apiResponseJson, Map.class);
      } catch (Exception e) {
        log.error("Failed to parse API response JSON from: {}", apiUrl, e);
      }
    }

    List<Map<String, Object>> chapters;
    if (apiResponse != null && apiResponse.containsKey(API_KEY_RESPONSE) && apiResponse.get(API_KEY_RESPONSE) instanceof List) {
      chapters = (List<Map<String, Object>>) apiResponse.get(API_KEY_RESPONSE);
      if (messages != null) {
        messages.add("Found " + chapters.size() + " chapters.");
      }
    } else {
      // Fallback: return single chapter with manga ID
      if (messages != null) {
        messages.add("API failed, using manga ID as single chapter.");
      }
      chapters = List.of();
    }

    // Convert chapters to list of chapter IDs (strings)
    var chapterIds = new ArrayList<String>();
    for (var chapter : chapters) {
      var chapterId = String.valueOf(chapter.get(API_KEY_ID));
      chapterIds.add(chapterId);
    }

    return chapterIds;
  }

  @Override
  public List<String> extractImageUrls(ChapterCrawlProcessingParams params) {
    var headers = crawlHttpClient.buildHeaders(API_BASE_URL);

    var apiUrl = API_BASE_URL + API_CHAPTER + "?id=" + params.url();
    var responseJson = crawlHttpClient.fetchUrl(apiUrl, headers, true);

    Map<String, Object> response = null;
    if (StringUtils.isNotEmpty(responseJson)) {
      try {
        response = objectMapper.readValue(responseJson, Map.class);
      } catch (Exception e) {
        log.error("Failed to parse API response JSON from: {}", apiUrl, e);
      }
    }

    if (response == null || !response.containsKey(API_KEY_PAGES)) {
      return emptyList();
    }

    var imageUrls = (List<String>) response.get(API_KEY_PAGES);

    return isNotEmpty(imageUrls) ? imageUrls : emptyList();
  }

  @Override
  public ChapterInfo detectChapterInfo(String url, Document doc, List<String> imageUrls, Object chapterData) {
    return comicDetectionService.detectChapterInfoMimi(chapterData, imageUrls);
  }

  @Override
  public boolean isHtmlBased() {
    return false;
  }

  @Override
  public String getBaseUrl() {
    return API_BASE_URL;
  }
}

