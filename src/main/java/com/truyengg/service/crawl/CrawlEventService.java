package com.truyengg.service.crawl;

import com.truyengg.domain.entity.CrawlEvent;
import com.truyengg.domain.enums.CrawlEventType;
import com.truyengg.domain.repository.ComicCrawlRepository;
import com.truyengg.domain.repository.CrawlEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlEventService {

  private final CrawlEventRepository eventRepository;
  private final ComicCrawlRepository comicCrawlRepository;
  private final ProgressMessagePublisher progressMessagePublisher;

  @Transactional
  public void createEvent(UUID crawlId, CrawlEventType eventType, String reason) {
    var crawl = comicCrawlRepository.findById(crawlId)
        .orElseThrow(() -> new IllegalArgumentException("Crawl not found: " + crawlId));

    var event = CrawlEvent.builder()
        .crawl(crawl)
        .eventType(eventType)
        .reason(reason)
        .build();

    eventRepository.save(event);

    // Publish event message to progress
    var eventMessage = formatEventMessage(eventType, reason);
    progressMessagePublisher.publishMessage(crawlId, eventMessage);
  }

  private String formatEventMessage(CrawlEventType eventType, String reason) {
    return "[EVENT] " + eventType.name() + ": " + reason;
  }

  public Optional<CrawlEvent> getLatestEventByCrawlIdAndType(UUID crawlId, CrawlEventType eventType) {
    return eventRepository.findLatestByCrawlIdAndEventType(crawlId, eventType);
  }

}

