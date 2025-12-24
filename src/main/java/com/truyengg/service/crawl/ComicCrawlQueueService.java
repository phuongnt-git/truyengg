package com.truyengg.service.crawl;

import com.truyengg.domain.repository.ComicCrawlRepository;
import com.truyengg.model.request.CrawlRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.truyengg.domain.enums.ComicCrawlStatus.PENDING;
import static com.truyengg.domain.enums.ComicCrawlStatus.RUNNING;
import static com.truyengg.model.dto.CrawlEvent.start;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComicCrawlQueueService {

  private final ComicCrawlRepository comicCrawlRepository;
  private final ComicCrawlLimitService comicCrawlLimitService;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public void processPendingCrawls() {
    var pendingCrawls = comicCrawlRepository.findByStatusOrderByCreatedAtAsc(PENDING);

    if (pendingCrawls.isEmpty()) {
      return;
    }

    for (var crawl : pendingCrawls) {
      if (tryStartPendingCrawl(crawl.getId())) {
        log.info("Started pending crawl {}", crawl.getId());
      }
    }
  }

  public boolean tryStartPendingCrawl(UUID crawlId) {
    var crawlOpt = comicCrawlRepository.findById(crawlId);
    if (crawlOpt.isEmpty()) {
      return false;
    }

    var crawl = crawlOpt.get();
    if (crawl.getStatus() != PENDING) {
      return false;
    }

    var userId = crawl.getCreatedBy().getId();
    if (!comicCrawlLimitService.canStartCrawl(userId)) {
      return false;
    }

    crawl.setStatus(RUNNING);
    comicCrawlRepository.save(crawl);

    var crawlRequest = new CrawlRequest(
        crawl.getUrl(),
        crawl.getDownloadMode(),
        crawl.getPartStart(),
        crawl.getPartEnd(),
        crawl.getDownloadChapters()
    );
    eventPublisher.publishEvent(start(crawlId, crawlRequest));

    return true;
  }

  @Transactional
  public void tryStartPendingCrawlsForUser(Long userId) {
    var pendingCrawls = comicCrawlRepository.findByStatusOrderByCreatedAtAsc(PENDING)
        .stream()
        .filter(crawl -> crawl.getCreatedBy().getId().equals(userId))
        .toList();

    for (var crawl : pendingCrawls) {
      if (tryStartPendingCrawl(crawl.getId())) {
        log.info("Started pending crawl {} for user {}", crawl.getId(), userId);
      }
    }
  }
}

