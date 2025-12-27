package com.truyengg.service.crawl.handler;

import com.truyengg.model.dto.ChapterCrawlProcessingParams;
import com.truyengg.model.dto.ChapterInfo;
import com.truyengg.model.dto.ComicInfo;
import com.truyengg.service.crawl.CrawlHttpClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.jsoup.nodes.Document;

import java.util.List;

import static org.apache.commons.lang3.StringUtils.EMPTY;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public abstract class CrawlHandler {

  CrawlHttpClient crawlHttpClient;

  public abstract ComicInfo detectComicInfo(String url, Document doc, Object apiResponse);

  public abstract List<String> extractChapterList(String url, String domain, List<String> messages);

  public abstract List<String> extractImageUrls(ChapterCrawlProcessingParams params);

  public abstract ChapterInfo detectChapterInfo(String url, Document doc, List<String> imageUrls, Object chapterData);

  public abstract boolean isHtmlBased();

  public String normalizeUrl(String url, String domain) {
    return crawlHttpClient.normalizeUrl(url, domain);
  }

  public String extractDomainFromUrl(String url) {
    return crawlHttpClient.extractDomainFromUrl(url);
  }

  public String getBaseUrl() {
    return EMPTY;
  }
}

