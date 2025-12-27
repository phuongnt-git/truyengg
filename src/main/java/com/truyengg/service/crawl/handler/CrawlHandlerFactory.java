package com.truyengg.service.crawl.handler;

import com.truyengg.domain.enums.CrawlSourceType;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CrawlHandlerFactory {

  HtmlCrawlHandler htmlCrawlHandler;
  ApiCrawlHandler apiCrawlHandler;

  public CrawlHandler getHandler(CrawlSourceType sourceType) {
    return switch (sourceType) {
      case API -> apiCrawlHandler;
      case HTML -> htmlCrawlHandler;
    };
  }

}

