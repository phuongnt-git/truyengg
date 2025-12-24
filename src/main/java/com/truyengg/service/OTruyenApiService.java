package com.truyengg.service;

import com.truyengg.config.OTruyenApiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Integer.parseInt;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@Service
@RequiredArgsConstructor
@Slf4j
public class OTruyenApiService {

  private final OTruyenApiConfig config;
  private final WebClient.Builder webClientBuilder;

  private WebClient getWebClient() {
    return webClientBuilder.baseUrl(config.getBaseUrl()).build();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> fetchData(String url) {
    try {
      return getWebClient().get()
          .uri(url)
          .retrieve()
          .bodyToMono(Map.class)
          .timeout(Duration.ofSeconds(10))
          .block();
    } catch (WebClientResponseException e) {
      log.error("HTTP Error: {} for URL: {}", e.getStatusCode(), url);
      return createErrorResponse("HTTP Error: " + e.getStatusCode());
    } catch (Exception e) {
      log.error("Error fetching data from URL: {}", url, e);
      return createErrorResponse("Error: " + e.getMessage());
    }
  }

  private Map<String, Object> createErrorResponse(String message) {
    var response = new HashMap<String, Object>();
    response.put("status", "error");
    response.put("msg", message);
    return response;
  }

  @Cacheable(value = "homeComics", key = "#page + '-' + #perPage")
  public Map<String, Object> getHomeComics(int page, int perPage) {
    var url = "/home?page=" + page + "&limit=" + perPage;
    return fetchData(url);
  }

  @Cacheable("categories")
  public Map<String, Object> getCategories() {
    return fetchData("/the-loai");
  }

  @Cacheable(value = "categoryComics", key = "#slug + '-' + #page + '-' + #perPage")
  public Map<String, Object> getComicsByCategory(String slug, int page, int perPage) {
    var url = "/the-loai/" + slug + "?page=" + page + "&limit=" + perPage;
    return fetchData(url);
  }

  @Cacheable(value = "comicDetails", key = "#slug")
  public Map<String, Object> getComicDetails(String slug) {
    return fetchData("/truyen-tranh/" + slug);
  }

  public Map<String, Object> searchComics(String query) {
    var url = "/tim-kiem?keywords=" + query;
    return fetchData(url);
  }

  @Cacheable(value = "comicList", key = "#type + '-' + #page + '-' + #perPage")
  public Map<String, Object> getComicList(String type, int page, int perPage) {
    var url = "/danh-sach/" + type + "?page=" + page + "&limit=" + perPage;
    return fetchData(url);
  }

  public Map<String, Object> advancedSearch(Map<String, Object> params) {
    var query = new StringBuilder();

    if (params.get("genres") != null && params.get("genres") instanceof List<?> genres && !genres.isEmpty()) {
      query.append("genres=").append(String.join(",", genres.stream().map(Object::toString).toList()));
    }

    if (params.get("notgenres") != null && params.get("notgenres") instanceof List<?> notGenres && !notGenres.isEmpty()) {
      if (!query.isEmpty()) query.append("&");
      query.append("notgenres=").append(String.join(",", notGenres.stream().map(Object::toString).toList()));
    }

    if (params.get("country") != null && !params.get("country").equals("0")) {
      if (!query.isEmpty()) query.append("&");
      query.append("country=").append(params.get("country"));
    }

    if (params.get("status") != null && !params.get("status").equals("-1")) {
      if (!query.isEmpty()) query.append("&");
      query.append("status=").append(params.get("status"));
    }

    if (params.get("minchapter") != null && !params.get("minchapter").equals("0")) {
      if (!query.isEmpty()) query.append("&");
      query.append("minchapter=").append(params.get("minchapter"));
    }

    if (params.get("sort") != null && !params.get("sort").equals("0")) {
      if (!query.isEmpty()) query.append("&");
      query.append("sort=").append(params.get("sort"));
    }

    var page = params.get("page") != null ? parseInt(params.get("page").toString()) : 1;
    query.append(!query.isEmpty() ? "&" : "").append("page=").append(page).append("&limit=24");

    var url = "/tim-kiem?" + query;
    return fetchData(url);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getChapterData(String chapterApiUrl) {
    if (chapterApiUrl.startsWith("http")) {
      return getWebClient().get()
          .uri(chapterApiUrl)
          .retrieve()
          .bodyToMono(Map.class)
          .timeout(Duration.ofSeconds(10))
          .block();
    }
    return fetchData(chapterApiUrl);
  }

  public String getImageUrl(String thumbUrl) {
    if (isEmpty(thumbUrl)) {
      return "https://st.truyengg.net/template/frontend/img/placeholder.jpg";
    }
    return config.getCdnImage() + "/uploads/comics/" + thumbUrl;
  }

  public String getTag(int chapterCount) {
    if (chapterCount > 50) {
      return "Hot";
    } else if (chapterCount < 10) {
      return "Mới";
    }
    return "";
  }
}
