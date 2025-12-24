package com.truyengg.service.crawl;

import com.truyengg.domain.entity.ComicCrawl;
import com.truyengg.domain.enums.ComicCrawlStatus;
import com.truyengg.domain.enums.CrawlEventType;
import com.truyengg.domain.repository.ComicCrawlRepository;
import com.truyengg.domain.repository.UserRepository;
import com.truyengg.model.request.CrawlJobFilterRequest;
import com.truyengg.model.request.CrawlRequest;
import com.truyengg.model.response.ComicCrawlProgressResponse;
import com.truyengg.service.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.truyengg.domain.enums.ComicCrawlStatus.CANCELLED;
import static com.truyengg.domain.enums.ComicCrawlStatus.PAUSED;
import static com.truyengg.domain.enums.ComicCrawlStatus.PENDING;
import static com.truyengg.domain.enums.ComicCrawlStatus.RUNNING;
import static com.truyengg.domain.specification.CrawlJobSpecifications.withFilter;
import static com.truyengg.model.dto.CrawlEvent.resume;
import static com.truyengg.model.dto.CrawlEvent.retry;
import static java.time.ZonedDateTime.now;
import static java.util.regex.Pattern.compile;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComicCrawlService {

  private static final String CRAWL_NOT_FOUND = "Crawl not found: ";

  private final ComicCrawlRepository comicCrawlRepository;
  private final UserRepository userRepository;
  private final PauseStateService pauseStateService;
  private final ApplicationEventPublisher eventPublisher;
  private final ComicCrawlCheckpointService comicCrawlCheckpointService;
  private final ComicCrawlProgressService comicCrawlProgressService;
  private final ChapterCrawlService chapterCrawlService;
  private final CrawlEventService crawlEventService;
  private final MinioService minioService;
  private final ComicCrawlLimitService comicCrawlLimitService;
  private final ComicCrawlQueueService comicCrawlQueueService;

  @Transactional
  public ComicCrawl createCrawl(CrawlRequest request, Long userId) {
    var user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

    var canStart = comicCrawlLimitService.canStartCrawl(userId);
    var crawlStatus = canStart ? RUNNING : PENDING;

    var crawl = ComicCrawl.builder()
        .status(crawlStatus)
        .url(request.url())
        .downloadMode(request.downloadMode())
        .downloadChapters(request.downloadChapters())
        .partStart(request.partStart())
        .partEnd(request.partEnd())
        .totalChapters(0)
        .downloadedChapters(0)
        .startTime(now())
        .createdBy(user)
        .build();

    var savedCrawl = comicCrawlRepository.save(crawl);

    if (canStart) {
      log.info("Created crawl {} with status RUNNING for user {}", savedCrawl.getId(), userId);
    } else {
      log.info("Created crawl {} with status PENDING for user {} (limit exceeded)", savedCrawl.getId(), userId);
    }

    return savedCrawl;
  }

  public Optional<ComicCrawl> getCrawlById(UUID crawlId) {
    return comicCrawlRepository.findById(crawlId);
  }

  public List<ComicCrawl> getRecentCrawls(int limit) {
    return comicCrawlRepository.findTop10ByOrderByCreatedAtDesc()
        .stream()
        .limit(limit)
        .toList();
  }

  public Page<ComicCrawl> getCrawlsWithFilter(CrawlJobFilterRequest filter, Pageable pageable) {
    var spec = withFilter(
        filter.status(),
        filter.createdBy(),
        filter.search(),
        filter.fromDate(),
        filter.toDate(),
        filter.includeDeleted() != null && filter.includeDeleted()
    );
    return comicCrawlRepository.findAll(spec, pageable);
  }

  @Transactional
  public void pauseCrawl(UUID crawlId, String reason) {
    var crawl = comicCrawlRepository.findById(crawlId)
        .orElseThrow(() -> new IllegalArgumentException(CRAWL_NOT_FOUND + crawlId));

    if (crawl.getStatus() != RUNNING) {
      throw new IllegalStateException("Chỉ có thể pause crawl đang RUNNING. Crawl hiện tại: " + crawl.getStatus());
    }

    crawl.setStatus(PAUSED);
    // Reason is stored in crawl_events, not in comic_crawl table
    comicCrawlRepository.save(crawl);

    pauseStateService.setPaused(crawlId);

    // Create pause event
    var eventReason = isNotEmpty(reason) ? reason : "Paused by user";
    crawlEventService.createEvent(crawlId, CrawlEventType.PAUSE, eventReason);
  }

  @Transactional
  public void resumeCrawl(UUID crawlId) {
    var crawl = comicCrawlRepository.findById(crawlId)
        .orElseThrow(() -> new IllegalArgumentException(CRAWL_NOT_FOUND + crawlId));

    if (crawl.getStatus() != PAUSED) {
      throw new IllegalStateException("Chỉ có thể resume crawl đang PAUSED. Crawl hiện tại: " + crawl.getStatus());
    }

    var checkpointOpt = comicCrawlCheckpointService.getCheckpoint(crawlId);
    if (checkpointOpt.isEmpty()) {
      throw new IllegalStateException("Không thể resume crawl không có checkpoint. Crawl có thể chưa được pause trước đó.");
    }

    var userId = crawl.getCreatedBy().getId();
    var canStart = comicCrawlLimitService.canStartCrawl(userId);

    if (canStart) {
      crawl.setStatus(RUNNING);
      comicCrawlRepository.save(crawl);

      pauseStateService.clearPaused(crawlId);

      crawlEventService.createEvent(crawlId, CrawlEventType.RESUME, "Resumed from checkpoint");

      var crawlRequest = new CrawlRequest(
          crawl.getUrl(),
          crawl.getDownloadMode(),
          crawl.getPartStart(),
          crawl.getPartEnd(),
          crawl.getDownloadChapters()
      );
      eventPublisher.publishEvent(resume(crawlId, crawlRequest));
    } else {
      crawl.setStatus(PENDING);
      comicCrawlRepository.save(crawl);
      log.info("Resume crawl {} set to PENDING for user {} (limit exceeded)", crawlId, userId);
    }
  }

  /**
   * Derive comicId from URL
   * Uses URL pattern detection to determine source type and extract comicId
   * Note: This may not be 100% accurate but is sufficient for deleting MinIO files during retry
   */
  private String deriveComicId(String url) {
    // Try MimiHentai pattern first: /g/(\d+)
    var mimiPattern = compile("/g/(\\d+)");
    var mimiMatcher = mimiPattern.matcher(url);
    if (mimiMatcher.find()) {
      var mangaId = mimiMatcher.group(1);
      return "mimi-" + mangaId;
    }

    var truyenQQNoPattern = compile("/truyen-tranh/([^/]+?)(?:/chapter|/chap|/chuong|/?$)");
    var truyenQQNoMatcher = truyenQQNoPattern.matcher(url);
    if (truyenQQNoMatcher.find()) {
      return truyenQQNoMatcher.group(1);
    }

    // For TruyenQQ and others, extract slug/comicId from URL
    // Try to extract from path (e.g., https://truyenqqgo.com/truyen/one-piece -> one-piece)
    try {
      var uri = new URI(url);
      var path = uri.getPath();
      if (path != null && path.length() > 1) {
        var segments = path.split("/");
        // Get last non-empty segment as potential comicId/slug
        for (int i = segments.length - 1; i >= 0; i--) {
          if (segments[i] != null && !segments[i].isEmpty() && !segments[i].equals("truyen")) {
            return segments[i];
          }
        }
      }
    } catch (Exception e) {
      log.warn("Failed to extract comicId from URL {}: {}", url, e.getMessage());
    }
    // Fallback: use a hash of URL or just "unknown" - files will be overwritten anyway
    return "unknown";
  }

  @Transactional
  public void retryCrawl(UUID crawlId) {
    var crawl = comicCrawlRepository.findById(crawlId)
        .orElseThrow(() -> new IllegalArgumentException(CRAWL_NOT_FOUND + crawlId));

    if (crawl.getStatus() == RUNNING || crawl.getStatus() == PAUSED) {
      throw new IllegalStateException("Không thể retry crawl đang RUNNING hoặc PAUSED. Vui lòng cancel crawl trước khi retry. Crawl hiện tại: " + crawl.getStatus());
    }

    if (crawl.getStatus() != ComicCrawlStatus.FAILED && crawl.getStatus() != ComicCrawlStatus.COMPLETED && crawl.getStatus() != ComicCrawlStatus.CANCELLED) {
      throw new IllegalStateException("Chỉ có thể retry crawl đã FAILED, COMPLETED hoặc CANCELLED. Crawl hiện tại: " + crawl.getStatus());
    }

    cleanupRetryCrawlData(crawlId, crawl);
    resetRetryCrawl(crawl);

    var userId = crawl.getCreatedBy().getId();
    var canStart = comicCrawlLimitService.canStartCrawl(userId);

    if (canStart) {
      crawl.setStatus(RUNNING);
      comicCrawlRepository.save(crawl);

      crawlEventService.createEvent(crawlId, CrawlEventType.RETRY, "Crawl retried");

      var crawlRequest = new CrawlRequest(
          crawl.getUrl(),
          crawl.getDownloadMode(),
          crawl.getPartStart(),
          crawl.getPartEnd(),
          crawl.getDownloadChapters()
      );
      eventPublisher.publishEvent(retry(crawlId, crawlRequest));
    } else {
      crawl.setStatus(PENDING);
      comicCrawlRepository.save(crawl);
      log.info("Retry crawl {} set to PENDING for user {} (limit exceeded)", crawlId, userId);
    }
  }

  private void cleanupRetryCrawlData(UUID crawlId, ComicCrawl crawl) {
    cleanupCheckpoint(crawlId);
    cleanupProgress(crawlId);
    cleanupMinioFiles(crawl);
    pauseStateService.remove(crawlId);
    // Note: Details are kept for retry tracking. Retry_count will be incremented
    // in strategies when chapters are actually retried (not here, to avoid incrementing
    // for chapters that won't be retried)
  }

  private void cleanupCheckpoint(UUID crawlId) {
    try {
      comicCrawlCheckpointService.deleteCheckpoint(crawlId);
    } catch (Exception e) {
      logCleanupError("checkpoint", crawlId, e);
    }
  }

  private void cleanupProgress(UUID crawlId) {
    try {
      comicCrawlProgressService.removeCrawl(crawlId);
    } catch (Exception e) {
      logCleanupError("progress", crawlId, e);
    }
  }

  private void cleanupMinioFiles(ComicCrawl crawl) {
    try {
      var comicId = deriveComicId(crawl.getUrl());
      minioService.deleteComicImages(comicId);
    } catch (Exception e) {
      logCleanupError("MinIO files", crawl.getId(), e);
    }
  }

  private void resetRetryCrawl(ComicCrawl crawl) {
    crawl.setStatus(RUNNING);
    crawl.setStartTime(now());
    crawl.setEndTime(null);
    crawl.setMessage(null);
    crawl.setTotalChapters(0);
    crawl.setDownloadedChapters(0);
    comicCrawlRepository.save(crawl);
  }

  private void logCleanupError(String resource, UUID crawlId, Exception e) {
    log.warn("Error deleting {} for crawl {} during retry: {}", resource, crawlId, e.getMessage());
  }

  @Transactional
  public void cancelCrawl(UUID crawlId, String reason) {
    var crawl = comicCrawlRepository.findById(crawlId)
        .orElseThrow(() -> new IllegalArgumentException(CRAWL_NOT_FOUND + crawlId));

    if (crawl.getStatus() == ComicCrawlStatus.COMPLETED || crawl.getStatus() == ComicCrawlStatus.CANCELLED) {
      throw new IllegalStateException("Không thể cancel crawl đã completed hoặc cancelled");
    }

    pauseStateService.setCancelled(crawlId);

    // Create cancel event
    var cancelReason = isNotEmpty(reason) ? reason : "Cancelled by user";
    crawlEventService.createEvent(crawlId, CrawlEventType.CANCEL, cancelReason);

    crawl.setStatus(ComicCrawlStatus.CANCELLED);
    crawl.setEndTime(now());
    if (isNotEmpty(reason)) {
      crawl.setMessage("Cancelled: " + reason);
    }
    comicCrawlRepository.save(crawl);

    updateProgressForCancelledCrawl(crawlId, reason);

    comicCrawlQueueService.processPendingCrawls();
  }

  private void updateProgressForCancelledCrawl(UUID crawlId, String reason) {
    try {
      var currentProgress = comicCrawlProgressService.getProgress(crawlId);
      if (currentProgress != null) {
        var cancelMessage = isNotEmpty(reason)
            ? "Cancelled: " + reason
            : "Crawl cancelled";
        var cancelledProgress = new ComicCrawlProgressResponse(
            currentProgress.crawlId(),
            CANCELLED,
            currentProgress.currentChapter(),
            currentProgress.totalChapters(),
            currentProgress.downloadedImages(),
            currentProgress.totalImages(),
            currentProgress.chapterProgress(),
            cancelMessage,
            currentProgress.messages(),
            currentProgress.startTime(),
            now(),
            currentProgress.elapsedSeconds()
        );
        comicCrawlProgressService.updateProgressCache(cancelledProgress);
        comicCrawlProgressService.persistProgress(crawlId);
      }
    } catch (Exception e) {
      log.warn("Failed to update progress for cancelled crawl {}: {}", crawlId, e.getMessage());
    }
  }

  @Transactional
  public void softDeleteCrawl(UUID crawlId, Long deletedBy) {
    var crawl = comicCrawlRepository.findById(crawlId)
        .orElseThrow(() -> new IllegalArgumentException(CRAWL_NOT_FOUND + crawlId));

    if (crawl.getDeletedAt() != null) {
      throw new IllegalStateException("Crawl đã bị xóa");
    }

    var deleteTime = now();
    crawl.setDeletedAt(deleteTime);
    crawl.setDeletedBy(deletedBy);
    comicCrawlRepository.save(crawl);
  }

  @Transactional
  public void hardDeleteCrawl(UUID crawlId, Long deletedBy) {
    var crawl = comicCrawlRepository.findById(crawlId)
        .orElseThrow(() -> new IllegalArgumentException(CRAWL_NOT_FOUND + crawlId));

    cleanupCrawlResources(crawlId);
    crawl.setDeletedBy(deletedBy);
    comicCrawlRepository.delete(crawl);
  }

  private void cleanupCrawlResources(UUID crawlId) {
    try {
      comicCrawlCheckpointService.deleteCheckpoint(crawlId);
    } catch (Exception e) {
      log.warn("Error deleting checkpoint for crawl {}: {}", crawlId, e.getMessage());
    }

    try {
      pauseStateService.remove(crawlId);
    } catch (Exception e) {
      log.warn("Error removing pause state for crawl {}: {}", crawlId, e.getMessage());
    }
  }

  @Transactional
  public void cleanupOldDetails(int daysOld) {
    chapterCrawlService.cleanupOldChapterCrawls(daysOld);
  }

  @Transactional
  public void restoreCrawl(UUID crawlId) {
    var crawl = comicCrawlRepository.findById(crawlId)
        .orElseThrow(() -> new IllegalArgumentException(CRAWL_NOT_FOUND + crawlId));

    if (crawl.getDeletedAt() == null) {
      throw new IllegalStateException("Crawl chưa bị xóa");
    }

    crawl.setDeletedAt(null);
    crawl.setDeletedBy(null);
    comicCrawlRepository.save(crawl);
  }

  @Transactional
  public void deleteAllCrawlsSoft(Long deletedBy, ComicCrawlStatus status) {
    var crawls = findCrawlsToDelete(status, true);
    var now = now();
    for (var crawl : crawls) {
      crawl.setDeletedAt(now);
      crawl.setDeletedBy(deletedBy);
    }

    comicCrawlRepository.saveAll(crawls);
  }

  @Transactional
  public void deleteAllCrawlsHard(ComicCrawlStatus status) {
    var crawls = findCrawlsToDelete(status, false);

    for (var crawl : crawls) {
      cleanupCrawlResources(crawl.getId());
    }

    comicCrawlRepository.deleteAll(crawls);
  }

  private List<ComicCrawl> findCrawlsToDelete(ComicCrawlStatus status, boolean softDelete) {
    if (status != null) {
      return softDelete
          ? comicCrawlRepository.findByStatusAndDeletedAtIsNull(status)
          : comicCrawlRepository.findByStatus(status);
    }
    return softDelete
        ? comicCrawlRepository.findByDeletedAtIsNull()
        : comicCrawlRepository.findAll();
  }
}

