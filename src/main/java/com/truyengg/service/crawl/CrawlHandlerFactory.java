package com.truyengg.service.crawl;

import com.truyengg.domain.enums.CrawlSourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CrawlHandlerFactory {

  private final HtmlCrawlHandler htmlCrawlHandler;
  private final ApiCrawlHandler apiCrawlHandler;

  public CrawlHandler getHandler(CrawlSourceType sourceType) {
    return switch (sourceType) {
      case API -> apiCrawlHandler;
      case HTML -> htmlCrawlHandler;
    };
  }

}

