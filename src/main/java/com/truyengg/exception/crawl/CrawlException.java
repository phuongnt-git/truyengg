package com.truyengg.exception.crawl;

public class CrawlException extends RuntimeException {

  public CrawlException(String message) {
    super(message);
  }

  public CrawlException(String message, Throwable cause) {
    super(message, cause);
  }
}

