package com.truyengg.service.crawl.handler;

import com.truyengg.model.dto.ChapterCrawlProcessingParams;
import com.truyengg.model.dto.ChapterInfo;
import com.truyengg.model.dto.ComicInfo;
import com.truyengg.service.comic.ComicDetectionService;
import com.truyengg.service.crawl.CrawlChapterExtractor;
import com.truyengg.service.crawl.CrawlHttpClient;
import com.truyengg.service.crawl.CrawlImageProcessor;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.List;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.jsoup.Jsoup.parse;

@Component
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class HtmlCrawlHandler extends CrawlHandler {

  ComicDetectionService comicDetectionService;
  CrawlChapterExtractor crawlChapterExtractor;
  CrawlImageProcessor crawlImageProcessor;

  public HtmlCrawlHandler(ComicDetectionService comicDetectionService,
                          CrawlChapterExtractor crawlChapterExtractor,
                          CrawlImageProcessor crawlImageProcessor,
                          CrawlHttpClient crawlHttpClient) {
    super(crawlHttpClient);
    this.comicDetectionService = comicDetectionService;
    this.crawlChapterExtractor = crawlChapterExtractor;
    this.crawlImageProcessor = crawlImageProcessor;
  }

  @Override
  public ComicInfo detectComicInfo(String url, Document doc, Object apiResponse) {
    return comicDetectionService.detectComicInfoHtmlBased(url, doc);
  }

  @Override
  public List<String> extractChapterList(String url, String domain, List<String> messages) {
    var headers = crawlHttpClient.buildHeaders(domain);
    var htmlContent = crawlHttpClient.fetchUrl(url, headers, false);

    if (isBlank(htmlContent)) {
      throw new IllegalStateException("Unable to load manga HTML: " + url);
    }

    var doc = parse(htmlContent);
    return crawlChapterExtractor.extractChapterList(domain, messages, doc);
  }

  @Override
  public List<String> extractImageUrls(ChapterCrawlProcessingParams params) {
    var headers = crawlHttpClient.buildHeaders(params.domain());
    var htmlContent = crawlHttpClient.fetchUrl(params.url(), headers, false);

    if (isBlank(htmlContent)) {
      return emptyList();
    }

    var doc = parse(htmlContent);
    return crawlImageProcessor.extractImageUrlsFromHtml(doc, params.domain());
  }

  @Override
  public ChapterInfo detectChapterInfo(String url, Document doc, List<String> imageUrls, Object chapterData) {
    return comicDetectionService.detectChapterInfo(url, doc, imageUrls);
  }

  @Override
  public boolean isHtmlBased() {
    return true;
  }
}

