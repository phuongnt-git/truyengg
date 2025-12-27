package com.truyengg.controller.api.admin;

import com.truyengg.domain.enums.CrawlStatus;
import com.truyengg.domain.enums.CrawlType;
import com.truyengg.model.dto.CrawlProgressDto;
import com.truyengg.model.dto.DuplicateCheckResult;
import com.truyengg.model.request.CrawlJobRequest;
import com.truyengg.model.response.ApiResponse;
import com.truyengg.model.response.CrawlJobResponse;
import com.truyengg.model.response.CrawlStatsResponse;
import com.truyengg.security.UserPrincipal;
import com.truyengg.service.auth.UserService;
import com.truyengg.service.crawl.CrawlJobService;
import com.truyengg.service.crawl.CrawlProgressService;
import com.truyengg.service.crawl.DuplicateDetectionService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.truyengg.model.response.ApiResponse.error;
import static com.truyengg.model.response.ApiResponse.success;
import static com.truyengg.model.response.CrawlJobResponse.from;
import static java.util.Collections.emptyList;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.ResponseEntity.badRequest;
import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.http.ResponseEntity.status;


/**
 * REST API controller for unified crawl job management.
 */
@Slf4j
@RestController
@RequestMapping("/api/crawls")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasRole('ADMIN')")
public class AdminCrawlController {

  CrawlJobService crawlJobService;
  CrawlProgressService progressService;
  DuplicateDetectionService duplicateService;
  UserService userService;

  /**
   * List all root crawl jobs with pagination.
   */
  @GetMapping
  public ResponseEntity<ApiResponse<Page<CrawlJobResponse>>> listCrawls(
      @RequestParam(required = false) CrawlType type,
      @RequestParam(required = false) CrawlStatus status,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
  ) {
    Page<CrawlJobResponse> jobs;

    if (type != null) {
      jobs = crawlJobService.findByType(type, pageable)
          .map(CrawlJobResponse::from);
    } else {
      jobs = crawlJobService.findAllRootJobs(pageable)
          .map(CrawlJobResponse::from);
    }

    return ok(success(jobs));
  }

  /**
   * Create a new crawl job.
   */
  @PostMapping
  public ResponseEntity<ApiResponse<CrawlJobResponse>> createCrawl(
      @Valid @RequestBody CrawlJobRequest request,
      @AuthenticationPrincipal UserPrincipal principal
  ) {
    var user = userService.getUserEntityById(principal.id());
    var job = crawlJobService.createAndStartJob(request, user);

    return status(CREATED)
        .body(success(from(job)));
  }

  /**
   * Get crawl job details.
   */
  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<CrawlJobResponse>> getCrawl(@PathVariable UUID id) {
    var job = crawlJobService.getById(id);
    return ok(success(from(job)));
  }

  /**
   * Delete crawl job (soft delete by default, hard delete with ?hard=true).
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> deleteCrawl(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "false") boolean hard
  ) {
    if (hard) {
      crawlJobService.hardDelete(id);
    } else {
      crawlJobService.softDelete(id);
    }

    return ok(success(null));
  }

  /**
   * Start a pending crawl job.
   */
  @PostMapping("/{id}/start")
  public ResponseEntity<ApiResponse<CrawlJobResponse>> startCrawl(@PathVariable UUID id) {
    var job = crawlJobService.start(id);
    return ok(success(CrawlJobResponse.from(job)));
  }

  /**
   * Pause a running crawl job.
   */
  @PostMapping("/{id}/pause")
  public ResponseEntity<ApiResponse<CrawlJobResponse>> pauseCrawl(@PathVariable UUID id) {
    var job = crawlJobService.pause(id);
    return ok(success(CrawlJobResponse.from(job)));
  }

  /**
   * Resume a paused crawl job.
   */
  @PostMapping("/{id}/resume")
  public ResponseEntity<ApiResponse<CrawlJobResponse>> resumeCrawl(@PathVariable UUID id) {
    var job = crawlJobService.resume(id);
    return ok(success(from(job)));
  }

  /**
   * Retry a failed crawl job.
   */
  @PostMapping("/{id}/retry")
  public ResponseEntity<ApiResponse<CrawlJobResponse>> retryCrawl(@PathVariable UUID id) {
    var job = crawlJobService.retry(id);
    return ok(success(from(job)));
  }

  /**
   * Cancel a crawl job.
   */
  @PostMapping("/{id}/cancel")
  public ResponseEntity<ApiResponse<CrawlJobResponse>> cancelCrawl(@PathVariable UUID id) {
    var job = crawlJobService.cancel(id);
    return ok(success(from(job)));
  }

  /**
   * Get child jobs of a crawl job.
   */
  @GetMapping("/{id}/children")
  public ResponseEntity<ApiResponse<List<CrawlJobResponse>>> getChildJobs(@PathVariable UUID id) {
    var children = crawlJobService.findChildJobs(id).stream()
        .map(CrawlJobResponse::from)
        .toList();
    return ok(success(children));
  }

  /**
   * Get progress of a crawl job.
   */
  @GetMapping("/{id}/progress")
  public ResponseEntity<ApiResponse<CrawlProgressDto>> getProgress(@PathVariable UUID id) {
    var progress = progressService.getProgressDto(id)
        .orElse(null);
    return ok(success(progress));
  }

  /**
   * Update crawl job settings.
   */
  @PutMapping("/{id}/settings")
  public ResponseEntity<ApiResponse<CrawlJobResponse>> updateSettings(
      @PathVariable UUID id,
      @RequestBody Map<String, Object> settings
  ) {
    var job = crawlJobService.updateSettings(id, settings);
    return ok(success(from(job)));
  }

  /**
   * Check for duplicates before creating a crawl.
   * Supports both single URL (via request param) and batch (via request body).
   */
  @PostMapping("/check-duplicates")
  public ResponseEntity<ApiResponse<List<DuplicateCheckResult>>> checkDuplicates(
      @RequestBody(required = false) Map<String, List<String>> body,
      @RequestParam(required = false) String url
  ) {
    List<String> urls;

    if (body != null && body.containsKey("urls")) {
      urls = body.get("urls");
    } else if (url != null) {
      urls = List.of(url);
    } else {
      return badRequest().body(error("No URLs provided"));
    }

    var resultsMap = duplicateService.batchCheckDuplicates(urls);
    var results = resultsMap.values().stream().toList();
    return ok(success(results));
  }

  /**
   * Restore a soft-deleted crawl job.
   */
  @PostMapping("/{id}/restore")
  public ResponseEntity<ApiResponse<Void>> restoreCrawl(@PathVariable UUID id) {
    crawlJobService.restore(id);
    return ok(success(null));
  }

  /**
   * List soft-deleted (trash) crawl jobs.
   */
  @GetMapping("/trash")
  public ResponseEntity<ApiResponse<List<CrawlJobResponse>>> listTrash() {
    // This would need a special query that bypasses @SQLRestriction
    // For now, return empty list
    return ok(success(emptyList()));
  }

  /**
   * Get crawl statistics.
   */
  @GetMapping("/stats")
  public ResponseEntity<ApiResponse<CrawlStatsResponse>> getStats() {
    var stats = crawlJobService.getStatsByTypeAndStatus();

    var byType = new HashMap<String, Long>();
    var byStatus = new HashMap<String, Long>();
    var total = 0;
    var active = 0;
    var completed = 0;
    var failed = 0;

    for (var row : stats) {
      var type = (CrawlType) row[0];
      var status = (CrawlStatus) row[1];
      var count = (Long) row[2];

      byType.merge(type.name(), count, Long::sum);
      byStatus.merge(status.name(), count, Long::sum);
      total += count;

      switch (status) {
        case PENDING, RUNNING, PAUSED -> active += count;
        case COMPLETED -> completed += count;
        case FAILED -> failed += count;
        default -> {
        }
      }
    }

    var response = new CrawlStatsResponse(total, active, completed, failed, byType, byStatus);
    return ok(success(response));
  }
}

