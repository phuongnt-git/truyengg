package com.truyengg.service.crawl;

import com.truyengg.domain.entity.CrawlJob;
import com.truyengg.domain.enums.CrawlType;
import com.truyengg.domain.exception.CrawlException;
import com.truyengg.service.crawl.handler.CrawlTypeHandler;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.truyengg.domain.exception.CrawlException.Reason.PAUSED;
import static java.util.Optional.of;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;

/**
 * Unified crawl executor that routes jobs to appropriate type handlers.
 * Uses Java 21 Virtual Threads for efficient async processing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CrawlExecutor {

  CrawlJobService jobService;
  CrawlProgressService progressService;
  CrawlCheckpointService checkpointService;
  List<CrawlTypeHandler> handlers;

  Map<CrawlType, Optional<CrawlTypeHandler>> handlerMap = new EnumMap<>(CrawlType.class);

  @PostConstruct
  void init() {
    for (var handler : handlers) {
      var type = handler.getSupportedType();
      if (type != null) {
        handlerMap.put(type, of(handler));
      }
    }
  }

  /**
   * Execute a crawl job asynchronously.
   * Determines the appropriate handler based on crawl type and delegates processing.
   */
  @Async("virtualThreadExecutor")
  public void execute(UUID jobId) {
    var job = jobService.getById(jobId);

    jobService.start(jobId);
    progressService.initProgress(jobId);

    var resumeIndex = getResumeIndex(jobId);
    doExecute(job, resumeIndex);
  }

  /**
   * Execute a crawl job with explicit resume index.
   */
  @Async("virtualThreadExecutor")
  public void executeWithResume(UUID jobId, int resumeFromIndex) {
    var job = jobService.getById(jobId);

    doExecute(job, resumeFromIndex);
  }

  // ===== Private methods =====

  private void doExecute(CrawlJob job, int startIndex) {
    var jobId = job.getId();

    try {
      var handler = getHandler(job.getCrawlType());

      if (startIndex > 0) {
        handler.handleWithResume(job, startIndex);
      } else {
        handler.handle(job);
      }

      onSuccess(jobId);

    } catch (CrawlException e) {
      onCrawlException(jobId, e);
    } catch (Exception e) {
      onFailure(jobId, e);
    }
  }

  private int getResumeIndex(UUID jobId) {
    return checkpointService.findByJobId(jobId)
        .filter(cp -> cp.getLastItemIndex() >= 0)
        .map(cp -> cp.getLastItemIndex() + 1)
        .orElse(0);
  }

  private void onSuccess(UUID jobId) {
    jobService.complete(jobId);
    progressService.finalize(jobId, "Crawl completed successfully");
  }

  private void onCrawlException(UUID jobId, CrawlException e) {
    if (e.getReason() == PAUSED) {
      progressService.updateMessage(jobId, "Paused at item " + (e.getLastIndex() + 1));
    } else {
      progressService.updateMessage(jobId, "Crawl cancelled");
    }
  }

  private void onFailure(UUID jobId, Exception e) {
    var errorMessage = getRootCauseMessage(e);
    jobService.fail(jobId, errorMessage);
    progressService.setError(jobId, errorMessage);
  }

  private CrawlTypeHandler getHandler(CrawlType type) {
    return handlerMap.get(type)
        .orElseThrow(() -> new IllegalArgumentException("No handler for crawl type: " + type));
  }
}
