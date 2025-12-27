package com.truyengg.service.crawl;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Generic chapter link extractor supporting multiple comic sites.
 */
@Slf4j
@Component
public class CrawlChapterExtractor {

  // URL patterns for chapter detection
  private static final Pattern CHAPTER_URL_PATTERN = Pattern.compile(
      "(?i)(chuong|chapter|chap|ch)[/-]?\\d+|/\\d+/?$"
  );

  // Text patterns for chapter detection (Vietnamese and English)
  private static final Pattern CHAPTER_TEXT_PATTERN = Pattern.compile(
      "(?i)^(chương|chapter|chap|ch\\.?)?\\s*\\d+.*$"
  );

  // Site-specific selectors (ordered by priority)
  private static final String[][] SITE_SELECTORS = {
      // TruyenQQ selectors
      {"truyenqq", "div.works-chapter-list a, div.list-chapter a, section.works-chapter-list a"},
      // NetTruyen selectors
      {"nettruyen", "div.list-chapter a, div#nt_listchapter a"},
      // MangaDex selectors
      {"mangadex", "div.chapter-list a, .chapter-feed a"},
      // Generic selectors
      {"*", "div.chapter-list a, div.list-chapter a, ul.chapter-list a"}
  };

  // Common container selectors
  private static final String[] CONTAINER_SELECTORS = {
      // ID-based (highest priority)
      "#list-chapter a",
      "#list_chapter a",
      "#nt_listchapter a",
      "#chapters a",
      // Class-based
      ".list-chapter a",
      ".list_chapter a",
      ".chapter-list a",
      ".chapters a",
      ".works-chapter-list a",
      ".works-chapter-item a",
      // Section/article containers
      "section[class*='chapter'] a",
      "article[class*='chapter'] a",
      // Div containers with chapter/list in class
      "div[class*='chapter-list'] a",
      "div[class*='list-chapter'] a",
      "ul[class*='chapter'] a",
      "ol[class*='chapter'] a"
  };

  // Fallback URL pattern selectors
  private static final String[] URL_PATTERN_SELECTORS = {
      "a[href*='chuong-']",
      "a[href*='/chuong/']",
      "a[href*='chapter-']",
      "a[href*='/chapter/']",
      "a[href*='chap-']",
      "a[href*='/chap/']",
      "a[href*='/ch-']"
  };

  /**
   * Extract chapter list from document with site-specific optimization.
   */
  public List<String> extractChapterList(String domain, List<String> messages, Document doc) {
    var chapterLinks = new Elements();
    var siteKey = detectSite(domain);

    // Step 1: Try site-specific selectors first
    chapterLinks = trySiteSpecificSelectors(doc, siteKey);

    // Step 2: Try common container selectors
    if (chapterLinks.isEmpty()) {
      chapterLinks = tryContainerSelectors(doc);
    }

    // Step 3: Try URL pattern selectors
    if (chapterLinks.isEmpty()) {
      chapterLinks = tryUrlPatternSelectors(doc);
    }

    // Step 4: Try generic chapter container detection
    if (chapterLinks.isEmpty()) {
      chapterLinks = tryGenericContainers(doc);
    }

    // Step 5: Fallback - scan all links for chapter patterns
    if (chapterLinks.isEmpty()) {
      chapterLinks = scanAllLinksForChapters(doc);
    }

    // Normalize and deduplicate URLs
    var chapterUrls = normalizeChapterUrls(chapterLinks, domain);

    if (isNotEmpty(messages)) {
      messages.add("Found " + chapterUrls.size() + " chapters.");
    }

    return chapterUrls;
  }

  /**
   * Detect site type from domain for optimized selector selection.
   */
  private String detectSite(String domain) {
    if (domain == null) return "*";
    var lowerDomain = domain.toLowerCase();
    if (lowerDomain.contains("truyenqq")) return "truyenqq";
    if (lowerDomain.contains("nettruyen")) return "nettruyen";
    if (lowerDomain.contains("mangadex")) return "mangadex";
    return "*";
  }

  /**
   * Try site-specific selectors.
   */
  private Elements trySiteSpecificSelectors(Document doc, String siteKey) {
    for (var siteSelector : SITE_SELECTORS) {
      if (siteSelector[0].equals(siteKey) || siteSelector[0].equals("*")) {
        var links = doc.select(siteSelector[1]);
        if (!links.isEmpty()) {
          return filterValidChapterLinks(links);
        }
      }
    }
    return new Elements();
  }

  /**
   * Try common container selectors.
   */
  private Elements tryContainerSelectors(Document doc) {
    for (var selector : CONTAINER_SELECTORS) {
      var links = doc.select(selector);
      if (!links.isEmpty()) {
        var filtered = filterValidChapterLinks(links);
        if (!filtered.isEmpty()) {
          return filtered;
        }
      }
    }
    return new Elements();
  }

  /**
   * Try URL pattern selectors.
   */
  private Elements tryUrlPatternSelectors(Document doc) {
    var combinedSelector = String.join(", ", URL_PATTERN_SELECTORS);
    var links = doc.select(combinedSelector);
    return filterValidChapterLinks(links);
  }

  /**
   * Try generic containers with chapter/list keywords.
   */
  private Elements tryGenericContainers(Document doc) {
    var containers = doc.select("""
        div[class*='chapter'], div[class*='list'],
        ul[class*='chapter'], ul[class*='list'],
        section[class*='chapter'], nav[class*='chapter']
        """.replace("\n", "").trim());

    var foundLinks = new Elements();
    for (var container : containers) {
      var links = container.select("a");
      for (var link : links) {
        if (isValidChapterLink(link)) {
          foundLinks.add(link);
        }
      }
    }
    return foundLinks;
  }

  /**
   * Scan all links in document for chapter patterns.
   */
  private Elements scanAllLinksForChapters(Document doc) {
    var allLinks = doc.select("a[href]");
    var foundLinks = new Elements();

    for (var link : allLinks) {
      if (isValidChapterLink(link)) {
        foundLinks.add(link);
      }
    }
    return foundLinks;
  }

  /**
   * Check if a link is a valid chapter link.
   */
  private boolean isValidChapterLink(Element link) {
    var href = link.attr("href");
    var text = link.text().trim();

    if (isBlank(href) || href.equals("#") || href.startsWith("javascript:")) {
      return false;
    }

    // Check URL pattern
    if (CHAPTER_URL_PATTERN.matcher(href).find()) {
      return true;
    }

    // Check text pattern
    if (isNotBlank(text) && CHAPTER_TEXT_PATTERN.matcher(text).matches()) {
      return true;
    }

    // Check Vietnamese chapter text
    var lowerText = text.toLowerCase();
    return lowerText.startsWith("chương") || lowerText.matches("^\\d+$");
  }

  /**
   * Filter elements to only include valid chapter links.
   */
  private Elements filterValidChapterLinks(Elements links) {
    var filtered = new Elements();
    for (var link : links) {
      if (isValidChapterLink(link)) {
        filtered.add(link);
      }
    }
    return filtered;
  }

  /**
   * Normalize chapter URLs to absolute URLs and remove duplicates.
   */
  public List<String> normalizeChapterUrls(Elements chapterLinks, String domain) {
    var chapterUrls = new ArrayList<String>();

    for (var link : chapterLinks) {
      var href = link.attr("href");
      if (isBlank(href)) continue;

      var fullUrl = EMPTY;
      if (href.startsWith("http://") || href.startsWith("https://")) {
        fullUrl = href;
      } else if (href.startsWith("//")) {
        fullUrl = "https:" + href;
      } else {
        var cleanHref = href.startsWith("/") ? href : "/" + href;
        fullUrl = domain + cleanHref;
      }

      chapterUrls.add(fullUrl);
    }

    // Remove duplicates while preserving order
    chapterUrls = new ArrayList<>(new LinkedHashSet<>(chapterUrls));

    // Reverse to get chapters in ascending order (oldest first)
    Collections.reverse(chapterUrls);

    return chapterUrls;
  }

}

