package com.truyengg.service.crawl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.truyengg.domain.entity.CrawlProgress;
import com.truyengg.domain.enums.ComicCrawlStatus;
import com.truyengg.domain.repository.ComicCrawlRepository;
import com.truyengg.domain.repository.CrawlProgressRepository;
import com.truyengg.model.response.ChapterCrawlProgress;
import com.truyengg.model.response.ComicCrawlProgressResponse;
import com.truyengg.service.WebSocketProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.truyengg.domain.enums.ComicCrawlStatus.CANCELLED;
import static com.truyengg.domain.enums.ComicCrawlStatus.COMPLETED;
import static com.truyengg.domain.enums.ComicCrawlStatus.FAILED;
import static com.truyengg.domain.enums.ComicCrawlStatus.PAUSED;
import static com.truyengg.service.crawl.CrawlConstants.ERROR_CRAWL_NOT_FOUND;
import static java.time.Duration.between;
import static java.time.ZonedDateTime.now;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComicCrawlProgressService {

  private final CrawlProgressRepository crawlProgressRepository;
  private final ComicCrawlRepository comicCrawlRepository;
  private final ComicCrawlCheckpointService comicCrawlCheckpointService;
  private final ChapterCrawlService chapterCrawlService;
  private final ObjectMapper objectMapper;
  private final WebSocketProgressService webSocketProgressService;
  private final ComicCrawlQueueService comicCrawlQueueService;

  private final Map<UUID, ComicCrawlProgressResponse> progressCache = new ConcurrentHashMap<>();

  @Transactional
  public void createProgress(UUID crawlId) {
    var startTime = now();

    var existingOpt = crawlProgressRepository.findByCrawlId(crawlId);
    if (existingOpt.isPresent()) {
      return;
    }

    var crawl = comicCrawlRepository.findById(crawlId)
        .orElseThrow(() -> new IllegalArgumentException(ERROR_CRAWL_NOT_FOUND + crawlId));

    var progress = CrawlProgress.builder()
        .crawl(crawl)
        .currentChapter(0)
        .totalChapters(0)
        .downloadedImages(0)
        .totalImages(0)
        .currentMessage("Đang khởi tạo...")
        .messages(emptyList())
        .startTime(startTime)
        .lastUpdate(startTime)
        .elapsedSeconds(0L)
        .build();

    crawlProgressRepository.save(progress);

    var progressResponse = getCrawlProgressResponse(progress);
    updateProgressCache(progressResponse);
  }

  /**
   * Update progress in cache and send via WebSocket (NO DB write)
   */
  public void updateProgressCache(ComicCrawlProgressResponse progress) {
    var crawlId = progress.crawlId();
    progressCache.put(crawlId, progress);
    webSocketProgressService.sendProgress(progress);
  }

  /**
   * Update cache with new messages (called by event listener)
   */
  public void addMessageToCache(UUID crawlId, String message) {
    progressCache.computeIfPresent(crawlId, (key, cached) -> {
      var updatedMessages = new ArrayList<>(cached.messages());
      updatedMessages.add(message);
      var updated = new ComicCrawlProgressResponse(cached, updatedMessages);
      webSocketProgressService.sendProgress(updated);
      return updated;
    });
  }


  /**
   * Persist progress to DB (async, only on final status)
   */
  @Async
  @Transactional
  public void persistProgress(UUID crawlId) {
    var progress = progressCache.get(crawlId);
    if (progress == null) {
      return;
    }

    var progressOpt = crawlProgressRepository.findByCrawlId(crawlId);
    var messagesList = extractMessagesList(progress, progressOpt);
    var progressEntity = progressOpt
        .map(crawlProgress -> updateProgressEntity(crawlProgress, progress))
        .orElseGet(() -> createProgressEntity(crawlId, progress, messagesList));
    crawlProgressRepository.save(progressEntity);
  }

  private List<String> extractMessagesList(ComicCrawlProgressResponse progress,
                                           java.util.Optional<CrawlProgress> progressOpt) {
    if (progress.messages() != null && !progress.messages().isEmpty()) {
      return new ArrayList<>(progress.messages());
    }
    if (progressOpt.isPresent() && progressOpt.get().getMessages() != null
        && !progressOpt.get().getMessages().isEmpty()) {
      return new ArrayList<>(progressOpt.get().getMessages());
    }
    return new ArrayList<>();
  }

  private CrawlProgress createProgressEntity(UUID crawlId, ComicCrawlProgressResponse progress,
                                             List<String> messagesList) {
    var crawl = comicCrawlRepository.findById(crawlId)
        .orElseThrow(() -> new IllegalArgumentException(ERROR_CRAWL_NOT_FOUND + crawlId));
    return CrawlProgress.builder()
        .crawl(crawl)
        .currentChapter(progress.currentChapter())
        .totalChapters(progress.totalChapters())
        .downloadedImages(progress.downloadedImages())
        .totalImages(progress.totalImages())
        .currentMessage(progress.currentMessage())
        .messages(messagesList)
        .startTime(progress.startTime())
        .lastUpdate(progress.lastUpdate())
        .elapsedSeconds(progress.elapsedSeconds())
        .build();
  }

  private CrawlProgress updateProgressEntity(CrawlProgress progressEntity,
                                             ComicCrawlProgressResponse progress) {
    progressEntity.setCurrentChapter(progress.currentChapter());
    progressEntity.setTotalChapters(progress.totalChapters());
    progressEntity.setDownloadedImages(progress.downloadedImages());
    progressEntity.setTotalImages(progress.totalImages());
    progressEntity.setCurrentMessage(progress.currentMessage());
    if (progress.messages() != null && !progress.messages().isEmpty()) {
      progressEntity.setMessages(new ArrayList<>(progress.messages()));
    } else if (progressEntity.getMessages() == null || progressEntity.getMessages().isEmpty()) {
      progressEntity.setMessages(new ArrayList<>());
    }
    progressEntity.setLastUpdate(progress.lastUpdate());
    progressEntity.setElapsedSeconds(progress.elapsedSeconds());
    return progressEntity;
  }


  public ComicCrawlProgressResponse calculateProgressFromDetails(UUID crawlId) {
    var crawlOpt = comicCrawlRepository.findById(crawlId);
    if (crawlOpt.isEmpty()) {
      return null;
    }
    var crawl = crawlOpt.get();

    int totalChapters;
    if (crawl.getPartStart() != null && crawl.getPartEnd() != null) {
      totalChapters = crawl.getPartEnd() - crawl.getPartStart() + 1;
    } else {
      totalChapters = (int) chapterCrawlService.countTotalChapterCrawls(crawlId);
    }

    var currentChapter = (int) chapterCrawlService.countCompletedChapterCrawls(crawlId);
    var downloadedImages = chapterCrawlService.getChapterCrawlByCrawlIdAll(crawlId).stream()
        .mapToInt(detail -> detail.getDownloadedImages() != null ? detail.getDownloadedImages() : 0)
        .sum();
    var totalImages = chapterCrawlService.getChapterCrawlByCrawlIdAll(crawlId).stream()
        .mapToInt(detail -> detail.getTotalImages() != null ? detail.getTotalImages() : 0)
        .sum();

    var progressOpt = crawlProgressRepository.findByCrawlId(crawlId);
    if (progressOpt.isEmpty()) {
      return null;
    }

    var progress = progressOpt.get();
    var status = crawl.getStatus();
    var startTime = progress.getStartTime();
    var lastUpdate = now();
    var elapsedSeconds = startTime != null ? between(startTime, lastUpdate).getSeconds() : 0L;

    var chapterProgressMap = parseChapterProgressFromCheckpoint(crawlId, status);

    return new ComicCrawlProgressResponse(
        crawlId,
        status,
        currentChapter,
        totalChapters,
        downloadedImages,
        totalImages,
        chapterProgressMap,
        progress.getCurrentMessage(),
        progress.getMessages() != null ? progress.getMessages() : emptyList(),
        startTime,
        lastUpdate,
        elapsedSeconds
    );
  }

  @Transactional(readOnly = true)
  public ComicCrawlProgressResponse getProgress(UUID crawlId) {
    var progressOpt = crawlProgressRepository.findByCrawlId(crawlId);

    if (progressOpt.isEmpty()) {
      return null;
    }

    var progress = progressOpt.get();
    var status = progress.getCrawl().getStatus();

    // Try to calculate from details first (more accurate)
    var detailsProgress = calculateProgressFromDetails(crawlId);
    if (detailsProgress != null && detailsProgress.totalChapters() > 0) {
      // Use details-based progress but keep messages and status from progress entity
      return new ComicCrawlProgressResponse(
          crawlId,
          status,
          detailsProgress.currentChapter(),
          detailsProgress.totalChapters(),
          detailsProgress.downloadedImages(),
          detailsProgress.totalImages(),
          detailsProgress.chapterProgress(),
          progress.getCurrentMessage(),
          progress.getMessages(),
          progress.getStartTime(),
          progress.getLastUpdate(),
          detailsProgress.elapsedSeconds()
      );
    }

    var chapterProgressMap = parseChapterProgressFromCheckpoint(crawlId, status);

    return new ComicCrawlProgressResponse(
        crawlId,
        status,
        progress.getCurrentChapter(),
        progress.getTotalChapters(),
        progress.getDownloadedImages(),
        progress.getTotalImages(),
        chapterProgressMap,
        progress.getCurrentMessage(),
        progress.getMessages(),
        progress.getStartTime(),
        progress.getLastUpdate(),
        progress.getElapsedSeconds()
    );
  }

  @Transactional
  public void finalizeCrawl(ComicCrawlProgressResponse finalProgress) {
    var crawlId = finalProgress.crawlId();
    var status = finalProgress.status();

    try {
      updateProgressCache(finalProgress);
      persistProgress(crawlId);
      updateCrawlStatus(crawlId, status, finalProgress);
      processCrawlCompletion(crawlId, status);
    } catch (Exception e) {
      log.warn("Error finalizing crawl: {}", crawlId, e);
      try {
        updateProgressCache(finalProgress);
        persistProgress(crawlId);
      } catch (Exception ex) {
        log.warn("Failed to update progress after finalize error: {}", crawlId, ex);
      }
    }
  }

  private void updateCrawlStatus(UUID crawlId, ComicCrawlStatus status,
                                 ComicCrawlProgressResponse finalProgress) {
    var crawl = comicCrawlRepository.findById(crawlId)
        .orElseThrow(() -> new IllegalArgumentException(ERROR_CRAWL_NOT_FOUND + crawlId));
    crawl.setStatus(status);
    crawl.setTotalChapters(finalProgress.totalChapters());
    crawl.setDownloadedChapters(finalProgress.currentChapter());

    if (status == ComicCrawlStatus.FAILED && !CollectionUtils.isEmpty(finalProgress.messages())) {
      crawl.setMessage(String.join("; ", finalProgress.messages()));
    }
    if (status == ComicCrawlStatus.COMPLETED || status == ComicCrawlStatus.FAILED || status == CANCELLED) {
      crawl.setEndTime(now());
    }
    comicCrawlRepository.save(crawl);
  }

  private void processCrawlCompletion(UUID crawlId, ComicCrawlStatus status) {
    if (status == ComicCrawlStatus.COMPLETED || status == ComicCrawlStatus.FAILED) {
      chapterCrawlService.aggregateChapterCrawlMetrics(crawlId);
      comicCrawlQueueService.processPendingCrawls();
    }
  }

  @Transactional
  public void removeCrawl(UUID crawlId) {
    crawlProgressRepository.deleteByCrawlId(crawlId);
  }

  private Map<String, ChapterCrawlProgress> parseChapterProgressFromCheckpoint(UUID crawlId,
                                                                               ComicCrawlStatus status) {
    if (status != PAUSED && status != CANCELLED) {
      return emptyMap();
    }
    var checkpointOpt = comicCrawlCheckpointService.getCheckpoint(crawlId);
    if (checkpointOpt.isEmpty() || checkpointOpt.get().getChapterProgress() == null) {
      return emptyMap();
    }
    try {
      var chapterProgressJson = checkpointOpt.get().getChapterProgress();
      var typeRef = new TypeReference<Map<String, ChapterCrawlProgress>>() {
      };
      return objectMapper.readValue(chapterProgressJson, typeRef);
    } catch (Exception e) {
      log.warn("Error parsing chapterCrawlProgress from checkpoint for crawl {}: {}", crawlId, e.getMessage());
      return emptyMap();
    }
  }

  private ComicCrawlProgressResponse getCrawlProgressResponse(CrawlProgress crawlProgress) {
    return new ComicCrawlProgressResponse(
        crawlProgress.getCrawl().getId(),
        crawlProgress.getCrawl().getStatus(),
        crawlProgress.getCurrentChapter(),
        crawlProgress.getTotalChapters(),
        crawlProgress.getDownloadedImages(),
        crawlProgress.getTotalImages(),
        emptyMap(),
        crawlProgress.getCurrentMessage(),
        crawlProgress.getMessages(),
        crawlProgress.getStartTime(),
        crawlProgress.getLastUpdate(),
        crawlProgress.getElapsedSeconds()
    );
  }

  @Transactional
  public void cleanupOldProgress() {
    var thirtyDaysAgo = now().minusDays(30);
    var allProgress = crawlProgressRepository.findAll();

    for (var progress : allProgress) {
      var status = progress.getCrawl().getStatus();
      var lastUpdate = progress.getLastUpdate();
      if ((status == COMPLETED || status == FAILED) &&
          lastUpdate.isBefore(thirtyDaysAgo)) {
        try {
          crawlProgressRepository.delete(progress);
        } catch (Exception e) {
          log.warn("Failed to delete old progress {}: {}", progress.getId(), e.getMessage());
        }
      }
    }
  }
}
