package com.truyengg.service.crawl;

import com.truyengg.domain.repository.ComicCrawlRepository;
import com.truyengg.model.dto.CrawlEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.truyengg.domain.enums.ComicCrawlStatus.FAILED;
import static com.truyengg.domain.enums.CrawlEventType.RETRY;
import static com.truyengg.domain.enums.CrawlEventType.START;
import static java.time.ZonedDateTime.now;
import static org.apache.commons.lang3.exception.ExceptionUtils.getMessage;

@Component
@RequiredArgsConstructor
@Slf4j
public class CrawlEventHandler {

  private final ComicCrawlProgressService comicCrawlProgressService;
  private final CrawlService crawlService;
  private final ComicCrawlRepository comicCrawlRepository;

  @EventListener
  @Async
  public void handleCrawlEvent(CrawlEvent event) {
    var crawlId = event.crawlId();
    var request = event.request();
    var eventType = event.type();

    try {
      if (eventType == START || eventType == RETRY) {
        comicCrawlProgressService.createProgress(crawlId);
      }
      crawlService.crawlMangaAsync(request, crawlId);
    } catch (Exception e) {
      if (eventType == START || eventType == RETRY) {
        updateCrawlStatus(crawlId, e);
      } else {
        log.warn("Error during crawl execution for crawl {} (type: {}): {}", crawlId, eventType, getMessage(e), e);
      }
    }
  }

  private void updateCrawlStatus(UUID crawlId, Exception exception) {
    try {
      var crawlOpt = comicCrawlRepository.findById(crawlId);
      if (crawlOpt.isPresent()) {
        var crawl = crawlOpt.get();
        crawl.setStatus(FAILED);
        crawl.setEndTime(now());
        crawl.setMessage("Initialization failed: " + getMessage(exception));
        comicCrawlRepository.save(crawl);
      }
    } catch (Exception e) {
      log.warn("Failed to update crawl {} status to FAILED: {}", crawlId, getMessage(e));
    }
  }

}

