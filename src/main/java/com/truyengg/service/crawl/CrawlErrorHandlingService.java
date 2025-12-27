package com.truyengg.service.crawl;

import com.truyengg.domain.entity.CrawlCheckpoint;
import com.truyengg.domain.entity.CrawlJob;
import com.truyengg.domain.enums.CrawlErrorType;
import com.truyengg.domain.enums.ErrorAction;
import com.truyengg.domain.repository.CrawlCheckpointRepository;
import com.truyengg.model.dto.CrawlErrorEvent;
import com.truyengg.model.dto.CrawlErrorResult;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.truyengg.domain.enums.CrawlErrorType.AUTH_REQUIRED;
import static com.truyengg.domain.enums.CrawlErrorType.BLOCKED;
import static com.truyengg.domain.enums.CrawlErrorType.CAPTCHA_REQUIRED;
import static com.truyengg.domain.enums.CrawlErrorType.NETWORK_ERROR;
import static com.truyengg.domain.enums.CrawlErrorType.NOT_FOUND;
import static com.truyengg.domain.enums.CrawlErrorType.PARSE_ERROR;
import static com.truyengg.domain.enums.CrawlErrorType.RATE_LIMITED;
import static com.truyengg.domain.enums.CrawlErrorType.TIMEOUT;
import static com.truyengg.domain.enums.ErrorAction.RETRY_DELAYED;
import static com.truyengg.domain.enums.ErrorAction.RETRY_IMMEDIATE;
import static com.truyengg.domain.enums.ErrorAction.SKIP_LOG;
import static com.truyengg.domain.enums.ErrorAction.SKIP_MARK_MANUAL;
import static com.truyengg.domain.enums.ErrorAction.SKIP_NOTIFY_ADMIN;
import static com.truyengg.domain.enums.ErrorAction.SKIP_PERMANENT;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Service for handling crawl errors with appropriate actions.
 * Implements Decision 5: Auto-skip captcha, mark for manual retry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CrawlErrorHandlingService {

  final CrawlCheckpointRepository checkpointRepository;
  final ApplicationEventPublisher eventPublisher;

  @Value("${crawl.error.max-retries:3}")
  int maxRetries;

  @Value("${crawl.error.retry-delay-ms:5000}")
  long retryDelayMs;

  /**
   * Determine action based on error type and retry count.
   */
  public ErrorAction determineAction(CrawlErrorType type, int retryCount) {
    return switch (type) {
      case NETWORK_ERROR, TIMEOUT -> retryCount < maxRetries
          ? RETRY_IMMEDIATE
          : RETRY_DELAYED;
      case RATE_LIMITED -> RETRY_DELAYED;
      case CAPTCHA_REQUIRED -> SKIP_MARK_MANUAL;
      case AUTH_REQUIRED, BLOCKED -> SKIP_NOTIFY_ADMIN;
      case NOT_FOUND -> SKIP_PERMANENT;
      case PARSE_ERROR -> SKIP_LOG;
    };
  }

  /**
   * Handle error with appropriate action.
   */
  @Transactional
  public CrawlErrorResult handleError(
      CrawlJob job,
      int itemIndex,
      CrawlErrorType errorType,
      String errorMessage
  ) {
    var action = determineAction(errorType, job.getRetryCount());

    return switch (action) {
      case RETRY_IMMEDIATE -> {
        job.setRetryCount(job.getRetryCount() + 1);
        yield new CrawlErrorResult(action, 0, false);
      }
      case RETRY_DELAYED -> {
        job.setRetryCount(job.getRetryCount() + 1);
        long delay = calculateBackoffDelay(job.getRetryCount());
        yield new CrawlErrorResult(action, delay, false);
      }
      case SKIP_MARK_MANUAL -> {
        markForManualRetry(job, itemIndex, errorType, errorMessage);
        yield new CrawlErrorResult(action, 0, true);
      }
      case SKIP_NOTIFY_ADMIN -> {
        markForManualRetry(job, itemIndex, errorType, errorMessage);
        notifyAdmin(job, errorType, errorMessage);
        yield new CrawlErrorResult(action, 0, true);
      }
      case SKIP_PERMANENT -> {
        markAsSkipped(job, itemIndex, errorMessage);
        yield new CrawlErrorResult(action, 0, true);
      }
      case SKIP_LOG -> {
        log.warn("Skipping item {} due to parse error: {}", itemIndex, errorMessage);
        markAsSkipped(job, itemIndex, errorMessage);
        yield new CrawlErrorResult(action, 0, true);
      }
    };
  }

  /**
   * Detect error type from exception and response body.
   */
  public CrawlErrorType detectErrorType(Exception e, String responseBody) {
    String message = ExceptionUtils.getRootCauseMessage(e).toLowerCase();

    if (e instanceof SocketTimeoutException || message.contains("timeout")) {
      return TIMEOUT;
    }
    if (e instanceof ConnectException || message.contains("connection")) {
      return NETWORK_ERROR;
    }
    if (message.contains("403") || message.contains("forbidden")) {
      return BLOCKED;
    }
    if (message.contains("404") || message.contains("not found")) {
      return NOT_FOUND;
    }
    if (message.contains("429") || message.contains("rate limit")) {
      return RATE_LIMITED;
    }

    // Check response body for captcha indicators
    if (isNotBlank(responseBody)) {
      var body = responseBody.toLowerCase();
      if (body.contains("captcha") || body.contains("recaptcha") ||
          body.contains("verify you are human")) {
        return CAPTCHA_REQUIRED;
      }
      if (body.contains("login") || body.contains("sign in") ||
          body.contains("authentication")) {
        return AUTH_REQUIRED;
      }
    }

    return PARSE_ERROR;
  }

  /**
   * Detect error type from exception only.
   */
  public CrawlErrorType detectErrorType(Exception e) {
    return detectErrorType(e, null);
  }

  // ===== Private methods =====

  private void markForManualRetry(
      CrawlJob job,
      int itemIndex,
      CrawlErrorType errorType,
      String errorMessage
  ) {
    var checkpoint = checkpointRepository.findById(job.getId())
        .orElseGet(() -> CrawlCheckpoint.builder()
            .crawlJob(job)
            .createdAt(ZonedDateTime.now())
            .updatedAt(ZonedDateTime.now())
            .build());

    // Add to failed indices
    var failedIndices = checkpoint.getFailedItemIndices();
    if (failedIndices == null) {
      failedIndices = new ArrayList<>();
    }
    if (!failedIndices.contains(itemIndex)) {
      failedIndices.add(itemIndex);
    }
    checkpoint.setFailedItemIndices(failedIndices);

    // Store error info in state snapshot
    var snapshot = checkpoint.getStateSnapshot();
    if (snapshot == null) {
      snapshot = new HashMap<>();
    }
    snapshot.put("lastError_" + itemIndex, Map.of(
        "type", errorType.name(),
        "message", errorMessage,
        "timestamp", ZonedDateTime.now().toString()
    ));
    checkpoint.setStateSnapshot(snapshot);
    checkpoint.setUpdatedAt(ZonedDateTime.now());

    checkpointRepository.save(checkpoint);

    log.info("Marked item {} for manual retry: {} - {}",
        itemIndex, errorType, errorMessage);
  }

  private void markAsSkipped(CrawlJob job, int itemIndex, String errorMessage) {
    var checkpoint = checkpointRepository.findById(job.getId())
        .orElseGet(() -> CrawlCheckpoint.builder()
            .crawlJob(job)
            .createdAt(ZonedDateTime.now())
            .updatedAt(ZonedDateTime.now())
            .build());

    checkpoint.addFailedIndex(itemIndex);
    checkpoint.setUpdatedAt(ZonedDateTime.now());
    checkpointRepository.save(checkpoint);
  }

  private long calculateBackoffDelay(int retryCount) {
    // Exponential backoff: 5s, 10s, 20s, 40s...
    return retryDelayMs * (long) Math.pow(2, retryCount - 1);
  }

  private void notifyAdmin(CrawlJob job, CrawlErrorType errorType, String errorMessage) {
    eventPublisher.publishEvent(new CrawlErrorEvent(
        job.getId(),
        job.getTargetUrl(),
        errorType,
        errorMessage
    ));
  }

}
