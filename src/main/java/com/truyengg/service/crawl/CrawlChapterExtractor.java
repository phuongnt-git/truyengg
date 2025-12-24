package com.truyengg.service.crawl;

import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static com.truyengg.service.crawl.CrawlConstants.PATTERN_CHAP;
import static com.truyengg.service.crawl.CrawlConstants.PATTERN_CHAPTER;
import static com.truyengg.service.crawl.CrawlConstants.PATTERN_CHUONG;
import static com.truyengg.service.crawl.CrawlConstants.PATTERN_NUMBERS;
import static com.truyengg.service.crawl.CrawlConstants.PROTOCOL_HTTP;
import static com.truyengg.service.crawl.CrawlConstants.PROTOCOL_HTTPS;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class CrawlChapterExtractor {

  public Elements extractChapterLinksFromContainers(Document doc) {
    var containers = doc.select("div[class*='list'], ul[class*='list'], div[class*='chapter'], ul[class*='chapter']");
    var foundLinks = new Elements();
    for (var container : containers) {
      var links = container.select("a");
      for (var link : links) {
        var href = link.attr("href");
        var linkText = link.text();
        if (isBlank(linkText)) {
          continue;
        }
        var text = linkText.toLowerCase();
        if (!href.isEmpty() && (href.contains(PATTERN_CHAPTER) || href.contains(PATTERN_CHAP) || href.contains(PATTERN_CHUONG) ||
            text.contains(PATTERN_CHAPTER) || text.contains(PATTERN_CHAP) || text.contains(PATTERN_CHUONG) || text.matches(PATTERN_NUMBERS))) {
          foundLinks.add(link);
        }
      }
    }
    return foundLinks;
  }

  public Elements extractChapterLinksFromAllLinks(Document doc) {
    var allLinks = doc.select("a[href*='" + PATTERN_CHAPTER + "'], a[href*='" + PATTERN_CHAP + "'], a[href*='" + PATTERN_CHUONG + "']");
    var foundLinks = new Elements();
    for (var link : allLinks) {
      var href = link.attr("href");
      var linkText = link.text();
      if (isBlank(linkText)) {
        continue;
      }
      var text = linkText.toLowerCase();
      if (isNotBlank(href) && (text.contains(PATTERN_CHAPTER) || text.contains(PATTERN_CHAP) || text.contains(PATTERN_CHUONG) || text.matches(PATTERN_NUMBERS))) {
        foundLinks.add(link);
      }
    }
    return foundLinks;
  }

  public List<String> normalizeChapterUrls(Elements chapterLinks, String domain) {
    var chapterUrls = new ArrayList<String>();
    for (var link : chapterLinks) {
      var href = link.attr("href");
      if (isNotBlank(href)) {
        if (href.startsWith(PROTOCOL_HTTP) || href.startsWith(PROTOCOL_HTTPS)) {
          chapterUrls.add(href);
        } else {
          var cleanHref = href.startsWith("/") ? href : "/" + href;
          chapterUrls.add(domain + cleanHref);
        }
      }
    }
    chapterUrls = new ArrayList<>(new LinkedHashSet<>(chapterUrls));
    Collections.reverse(chapterUrls);
    return chapterUrls;
  }

  public List<String> extractChapterList(String domain, List<String> messages, Document doc) {
    // Get chapter list - try multiple selectors
    var chapterLinks = doc.select("div.works-chapter-item a");

    // If not found, try other common selectors
    if (chapterLinks.isEmpty()) {
      chapterLinks = doc.select("div.chapter-list a, div.list-chapter a, ul.chapter-list a, .chapter-item a, .works-chapter a");
    }

    // Try selector for generic chapter containers
    if (chapterLinks.isEmpty()) {
      chapterLinks = doc.select("div[class*='chapter'] a, ul[class*='chapter'] a, li[class*='chapter'] a");
    }

    // If still not found, try finding all links containing "chapter" or "chap" in href or text
    if (chapterLinks.isEmpty()) {
      var foundLinks = extractChapterLinksFromAllLinks(doc);
      chapterLinks.addAll(foundLinks);
    }

    // If still not found, try finding in div/ul with class containing "list" or "chapter"
    if (chapterLinks.isEmpty()) {
      var foundLinks = extractChapterLinksFromContainers(doc);
      chapterLinks.addAll(foundLinks);
    }

    var chapterUrls = normalizeChapterUrls(chapterLinks, domain);
    if (messages != null) {
      messages.add("Found " + chapterUrls.size() + " chapters.");
    }

    return chapterUrls;
  }
}

