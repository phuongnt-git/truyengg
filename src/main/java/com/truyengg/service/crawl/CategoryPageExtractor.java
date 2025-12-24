package com.truyengg.service.crawl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@Component
@RequiredArgsConstructor
@Slf4j
public class CategoryPageExtractor {

  private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile("trang[_-]?(\\d+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern PAGE_NUMBER_QUERY_PATTERN = Pattern.compile("[?&]page[=:]?(\\d+)", Pattern.CASE_INSENSITIVE);
  private final CrawlHttpClient crawlHttpClient;

  public List<String> extractPaginationLinks(Document doc, String baseUrl) {
    var paginationLinks = new HashSet<String>();

    // Try common pagination selectors
    var paginationSelectors = List.of(
        "div.pagination a",
        "ul.pagination a",
        "nav.pagination a",
        "div.paging a",
        "ul.paging a",
        ".pagination a",
        ".paging a",
        "a[href*='trang']",
        "a[href*='page']"
    );

    for (var selector : paginationSelectors) {
      var links = doc.select(selector);
      for (var link : links) {
        var href = link.attr("href");
        if (isNotEmpty(href)) {
          var absoluteUrl = normalizeUrl(href, baseUrl);
          if (absoluteUrl != null && (absoluteUrl.contains("trang") || absoluteUrl.contains("page"))) {
            paginationLinks.add(absoluteUrl);
          }
        }
      }
    }

    return new ArrayList<>(paginationLinks);
  }

  public int getMaxPageNumber(String categoryUrl) {
    try {
      var domain = crawlHttpClient.extractDomainFromUrl(categoryUrl);
      var headers = crawlHttpClient.buildHeaders(domain);
      var htmlContent = crawlHttpClient.fetchUrl(categoryUrl, headers, false);

      if (!isNotEmpty(htmlContent)) {
        log.warn("Unable to load category page: {}", categoryUrl);
        return 1;
      }

      var doc = Jsoup.parse(htmlContent);
      var paginationLinks = extractPaginationLinks(doc, categoryUrl);

      int maxPage = 1;

      // Extract page numbers from pagination links
      for (var link : paginationLinks) {
        var pageNum = extractPageNumber(link);
        if (pageNum > maxPage) {
          maxPage = pageNum;
        }
      }

      // Also check the current URL
      var currentPageNum = extractPageNumber(categoryUrl);
      if (currentPageNum > maxPage) {
        maxPage = currentPageNum;
      }

      // Try to find "next" or last page link
      var nextLinks = doc.select("a:contains(›), a:contains(»), a:contains(Cuối), a:contains(Last), a:contains(Next)");
      for (var nextLink : nextLinks) {
        var href = nextLink.attr("href");
        if (isNotEmpty(href)) {
          var absoluteUrl = normalizeUrl(href, categoryUrl);
          if (absoluteUrl != null) {
            var pageNum = extractPageNumber(absoluteUrl);
            if (pageNum > maxPage) {
              maxPage = pageNum;
            }
          }
        }
      }

      log.info("Found max page number: {} for category: {}", maxPage, categoryUrl);
      return maxPage;
    } catch (Exception e) {
      log.error("Error extracting max page number from: {}", categoryUrl, e);
      return 1;
    }
  }

  public List<String> extractStoryLinks(Document doc, String baseUrl) {
    var storyUrls = new HashSet<String>();

    // Try common story link selectors for truyenqqno.com
    var storySelectors = List.of(
        "div.list-story-item a",
        "div.story-item a",
        "div.item-story a",
        "div.comic-item a",
        "a[href*='/truyen-tranh/']",
        "a[href*='/truyen/']",
        ".list-story a",
        ".story-list a"
    );

    for (var selector : storySelectors) {
      var links = doc.select(selector);
      for (var link : links) {
        var href = link.attr("href");
        if (isNotEmpty(href)) {
          var absoluteUrl = normalizeUrl(href, baseUrl);
          if (isStoryUrl(absoluteUrl)) {
            storyUrls.add(absoluteUrl);
          }
        }
      }
    }

    // If no stories found with specific selectors, try finding all links containing story patterns
    if (storyUrls.isEmpty()) {
      var allLinks = doc.select("a[href]");
      for (var link : allLinks) {
        var href = link.attr("href");
        if (isNotEmpty(href)) {
          var absoluteUrl = normalizeUrl(href, baseUrl);
          if (isStoryUrl(absoluteUrl)) {
            // Check if it's not a chapter link
            if (!absoluteUrl.contains("/chapter") && !absoluteUrl.contains("/chap")) {
              storyUrls.add(absoluteUrl);
            }
          }
        }
      }
    }

    return new ArrayList<>(storyUrls);
  }

  public List<String> extractStoryUrlsFromPage(String pageUrl) {
    try {
      var domain = crawlHttpClient.extractDomainFromUrl(pageUrl);
      var headers = crawlHttpClient.buildHeaders(domain);
      var htmlContent = crawlHttpClient.fetchUrl(pageUrl, headers, false);

      if (!isNotEmpty(htmlContent)) {
        log.warn("Unable to load page: {}", pageUrl);
        return List.of();
      }

      var doc = Jsoup.parse(htmlContent);
      return extractStoryLinks(doc, pageUrl);
    } catch (Exception e) {
      log.error("Error extracting story URLs from page: {}", pageUrl, e);
      return List.of();
    }
  }

  private int extractPageNumber(String url) {
    if (url == null || url.isEmpty()) {
      return 1;
    }

    // Try pattern "trang-6" or "trang_6" or "trang6"
    var matcher = PAGE_NUMBER_PATTERN.matcher(url);
    if (matcher.find()) {
      try {
        return Integer.parseInt(matcher.group(1));
      } catch (NumberFormatException e) {
        // Ignore
      }
    }

    // Try query parameter "?page=6" or "&page=6"
    matcher = PAGE_NUMBER_QUERY_PATTERN.matcher(url);
    if (matcher.find()) {
      try {
        return Integer.parseInt(matcher.group(1));
      } catch (NumberFormatException e) {
        // Ignore
      }
    }

    return 1;
  }

  private String normalizeUrl(String href, String baseUrl) {
    if (href == null || href.isEmpty()) {
      return null;
    }

    try {
      var domain = crawlHttpClient.extractDomainFromUrl(baseUrl);
      return crawlHttpClient.normalizeUrl(href, domain);
    } catch (Exception e) {
      log.warn("Error normalizing URL: {} with base: {}", href, baseUrl, e);
      return null;
    }
  }

  private boolean isStoryUrl(String url) {
    if (url == null || url.isEmpty()) {
      return false;
    }

    // Check if URL looks like a story URL (not chapter, not category, not other pages)
    var lowerUrl = url.toLowerCase();
    return (lowerUrl.contains("/truyen-tranh/") || lowerUrl.contains("/truyen/") || lowerUrl.contains("/manga/"))
        && !lowerUrl.contains("/chapter")
        && !lowerUrl.contains("/chap")
        && !lowerUrl.contains("/the-loai")
        && !lowerUrl.contains("/category")
        && !lowerUrl.contains("/trang")
        && !lowerUrl.contains("/page")
        && !lowerUrl.contains("/tim-kiem")
        && !lowerUrl.contains("/search");
  }
}

