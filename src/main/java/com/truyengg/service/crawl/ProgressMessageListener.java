package com.truyengg.service.crawl;

import com.truyengg.model.dto.ProgressMessageEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProgressMessageListener {

  private final ComicCrawlProgressService progressService;

  @EventListener
  @Async
  public void handleProgressMessage(ProgressMessageEvent event) {
    progressService.addMessageToCache(event.crawlId(), event.message());
  }
}

