package com.truyengg.service;

import com.truyengg.model.response.CategoryCrawlProgressResponse;
import com.truyengg.model.response.ComicCrawlProgressResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketProgressService {

  private final SimpMessagingTemplate messagingTemplate;

  public void sendProgress(ComicCrawlProgressResponse progress) {
    var crawlId = progress.crawlId();

    try {
      if (isEmpty(crawlId) || isEmpty(progress)) {
        return;
      }

      var destination = "/topic/crawl-progress/" + crawlId;
      messagingTemplate.convertAndSend(destination, progress);
    } catch (Exception e) {
      log.error("Error sending WebSocket progress update for crawl: {}", crawlId, e);
    }
  }

  public void sendCategoryCrawlProgress(UUID categoryJobId, CategoryCrawlProgressResponse progress) {
    try {
      if (isEmpty(categoryJobId) || isEmpty(progress)) {
        return;
      }

      var destination = "/topic/category-crawl-progress/" + categoryJobId;
      messagingTemplate.convertAndSend(destination, progress);
    } catch (Exception e) {
      log.error("Error sending WebSocket progress update for category crawl job: {}", categoryJobId, e);
    }
  }
}
