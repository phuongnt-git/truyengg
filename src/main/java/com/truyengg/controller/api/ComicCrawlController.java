package com.truyengg.controller.api;

import com.truyengg.domain.enums.ComicCrawlStatus;
import com.truyengg.model.dto.CommicCrawlCheckpointResponse;
import com.truyengg.model.request.CrawlJobFilterRequest;
import com.truyengg.model.request.CrawlRequest;
import com.truyengg.model.response.ApiResponse;
import com.truyengg.model.response.ComicCrawlDetailResponse;
import com.truyengg.model.response.ComicCrawlProgressResponse;
import com.truyengg.model.response.ComicCrawlResponse;
import com.truyengg.security.UserPrincipal;
import com.truyengg.service.UserService;
import com.truyengg.service.crawl.ChapterCrawlMapper;
import com.truyengg.service.crawl.ChapterCrawlService;
import com.truyengg.service.crawl.ComicCrawlCheckpointService;
import com.truyengg.service.crawl.ComicCrawlProgressService;
import com.truyengg.service.crawl.ComicCrawlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.truyengg.domain.enums.ComicCrawlStatus.PENDING;
import static com.truyengg.model.dto.CrawlEvent.start;
import static com.truyengg.model.response.ApiResponse.success;
import static com.truyengg.model.response.ComicCrawlResponse.fromEntity;
import static java.time.ZonedDateTime.parse;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.springframework.http.ResponseEntity.ok;

@Tag(name = "Crawl", description = "Crawl manga APIs")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class ComicCrawlController {

  private static final String CRAWL_NOT_EXISTS = "Crawl không tồn tại";

  private final ComicCrawlService comicCrawlService;
  private final ComicCrawlCheckpointService comicCrawlCheckpointService;
  private final ChapterCrawlService chapterCrawlService;
  private final ComicCrawlProgressService comicCrawlProgressService;
  private final UserService userService;
  private final ApplicationEventPublisher eventPublisher;

  @PostMapping("/comic-crawls/async")
  @Operation(summary = "Start async comic crawl", description = "Start async comic crawl and return crawl ID")
  public ResponseEntity<ApiResponse<UUID>> startComicCrawlAsync(
      @Valid @RequestBody CrawlRequest request,
      @AuthenticationPrincipal UserPrincipal userPrincipal) {
    var crawl = comicCrawlService.createCrawl(request, userPrincipal.getId());
    var crawlId = crawl.getId();

    if (crawl.getStatus() == PENDING) {
      return ok(success("Crawl created and queued. Will start automatically when slot becomes available.", crawlId));
    }

    eventPublisher.publishEvent(start(crawlId, request));
    return ok(success(crawlId));
  }

  @GetMapping("/comic-crawls")
  @Operation(summary = "Get comic crawls with filter", description = "Get paginated list of comic crawls with filter and search")
  public ResponseEntity<ApiResponse<Page<ComicCrawlResponse>>> getComicCrawlsWithFilter(
      @RequestParam(required = false) ComicCrawlStatus status,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) Long createdBy,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String fromDate,
      @RequestParam(required = false) String toDate,
      @RequestParam(required = false, defaultValue = "false") Boolean includeDeleted,
      @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    var fromDateParsed = isNotEmpty(fromDate) ? parse(fromDate) : null;
    var toDateParsed = isNotEmpty(toDate) ? parse(toDate) : null;
    var filter = new CrawlJobFilterRequest(status, source, createdBy, search, fromDateParsed, toDateParsed, includeDeleted);
    var page = comicCrawlService.getCrawlsWithFilter(filter, pageable);

    var responsePage = page.map(crawl -> {
      var username = userService.getCurrentUserProfile(crawl.getCreatedBy().getId()).username();
      return fromEntity(crawl, username);
    });

    return ok(success(responsePage));
  }

  @GetMapping("/comic-crawls/{crawlId}")
  @Operation(summary = "Get comic crawl details", description = "Get detailed information about a specific comic crawl")
  public ResponseEntity<ApiResponse<ComicCrawlDetailResponse>> getComicCrawlDetail(
      @PathVariable UUID crawlId,
      @PageableDefault(size = 10, sort = "chapterIndex", direction = Sort.Direction.ASC) Pageable pageable) {
    var crawlOpt = comicCrawlService.getCrawlById(crawlId);
    if (crawlOpt.isEmpty()) {
      return ok(ApiResponse.error(CRAWL_NOT_EXISTS));
    }

    var crawl = crawlOpt.get();
    var username = userService.getCurrentUserProfile(crawl.getCreatedBy().getId()).username();
    var crawlResponse = fromEntity(crawl, username);

    // Get checkpoint if exists
    var checkpointOpt = comicCrawlCheckpointService.getCheckpoint(crawlId);
    CommicCrawlCheckpointResponse checkpoint = null;
    if (checkpointOpt.isPresent()) {
      var cp = checkpointOpt.get();
      // Convert entity to DTO
      checkpoint = new CommicCrawlCheckpointResponse(
          cp.getCurrentChapterIndex(),
          cp.getCrawledChapters() != null ? cp.getCrawledChapters() : new ArrayList<>(),
          null // chapterCrawlProgress - có thể parse từ JSON nếu cần
      );
    }

    // Get current progress for all crawl statuses (including completed/failed for history)
    ComicCrawlProgressResponse currentProgress = null;
    try {
      currentProgress = comicCrawlProgressService.getProgress(crawlId);
    } catch (Exception e) {
      // Progress might not exist for very old crawls, ignore
      log.debug("Progress not found for crawl {}: {}", crawlId, e.getMessage());
    }

    // Get downloaded files list (simplified - return empty list for now)
    // TODO: Implement file listing from MinIO
    var downloadedFiles = Collections.<String>emptyList();

    // Get crawl details with pagination
    var detailsPage = chapterCrawlService.getChapterCrawlByCrawlId(crawlId, pageable);
    var details = detailsPage.map(ChapterCrawlMapper::toDto);

    // Get retry statistics and create metrics DTO
    var retryStats = chapterCrawlService.getRetryStatisticsByCrawlId(crawlId);
    var metrics = ChapterCrawlMapper.toMetricsDto(crawl, retryStats);

    var detailResponse = new ComicCrawlDetailResponse(
        crawlResponse,
        checkpoint,
        currentProgress,
        downloadedFiles,
        details,
        metrics
    );

    return ok(success(detailResponse));
  }

  @GetMapping("/comic-crawls/recent")
  @Operation(summary = "Get recent comic crawls", description = "Get recent comic crawls (limit 10)")
  public ResponseEntity<ApiResponse<List<ComicCrawlResponse>>> getRecentComicCrawls() {
    var crawls = comicCrawlService.getRecentCrawls(10);

    var responses = crawls.stream()
        .map(crawl -> {
          String username = userService.getCurrentUserProfile(crawl.getCreatedBy().getId()).username();
          return fromEntity(crawl, username);
        })
        .toList();

    return ok(success(responses));
  }

  @PostMapping("/comic-crawls/{crawlId}/pause")
  @Operation(summary = "Pause comic crawl", description = "Pause a running comic crawl")
  public ResponseEntity<ApiResponse<Object>> pauseComicCrawl(
      @PathVariable UUID crawlId,
      @RequestParam(required = false) String reason) {
    try {
      comicCrawlService.pauseCrawl(crawlId, reason);
      return ok(success("Crawl đã được pause"));
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(ApiResponse.error(e.getMessage()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(ApiResponse.error(e.getMessage()));
    }
  }

  @PostMapping("/comic-crawls/{crawlId}/resume")
  @Operation(summary = "Resume comic crawl", description = "Resume a paused comic crawl")
  public ResponseEntity<ApiResponse<Object>> resumeComicCrawl(
      @PathVariable UUID crawlId) {
    try {
      comicCrawlService.resumeCrawl(crawlId);
      return ok(success("Crawl đã được resume"));
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(ApiResponse.error(e.getMessage()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(ApiResponse.error(e.getMessage()));
    }
  }

  @PostMapping("/comic-crawls/{crawlId}/cancel")
  @Operation(summary = "Cancel comic crawl", description = "Cancel a running or paused comic crawl")
  public ResponseEntity<ApiResponse<Object>> cancelComicCrawl(
      @PathVariable UUID crawlId,
      @RequestParam(required = false) String reason) {
    try {
      comicCrawlService.cancelCrawl(crawlId, reason);
      return ok(success("Crawl đã được cancel"));
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(ApiResponse.error(e.getMessage()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(ApiResponse.error(e.getMessage()));
    }
  }

  @PostMapping("/comic-crawls/{crawlId}/retry")
  @Operation(summary = "Retry comic crawl", description = "Retry a failed, completed, or cancelled comic crawl from scratch")
  public ResponseEntity<ApiResponse<Object>> retryComicCrawl(
      @PathVariable UUID crawlId) {
    try {
      comicCrawlService.retryCrawl(crawlId);
      return ok(success("Crawl đã được retry"));
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(ApiResponse.error(e.getMessage()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(ApiResponse.error(e.getMessage()));
    }
  }

  @DeleteMapping("/comic-crawls/{crawlId}")
  @Operation(summary = "Delete comic crawl", description = "Delete a comic crawl (soft delete by default, hard delete if specified)")
  public ResponseEntity<ApiResponse<Object>> deleteComicCrawl(
      @PathVariable UUID crawlId,
      @RequestParam(defaultValue = "false") boolean hardDelete,
      @AuthenticationPrincipal UserPrincipal userPrincipal) {
    try {
      if (hardDelete) {
        comicCrawlProgressService.removeCrawl(crawlId);
        comicCrawlService.hardDeleteCrawl(crawlId, userPrincipal.getId());
        return ok(success("Crawl đã được xóa vĩnh viễn"));
      } else {
        comicCrawlService.softDeleteCrawl(crawlId, userPrincipal.getId());
        return ok(success("Crawl đã được xóa (có thể khôi phục)"));
      }
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(ApiResponse.error(e.getMessage()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(ApiResponse.error(e.getMessage()));
    }
  }

  @PostMapping("/comic-crawls/{crawlId}/restore")
  @Operation(summary = "Restore comic crawl", description = "Restore a soft-deleted comic crawl")
  public ResponseEntity<ApiResponse<Object>> restoreComicCrawl(@PathVariable UUID crawlId) {
    try {
      comicCrawlService.restoreCrawl(crawlId);
      return ok(success("Crawl đã được khôi phục"));
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(ApiResponse.error(e.getMessage()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(ApiResponse.error(e.getMessage()));
    }
  }

  @DeleteMapping("/comic-crawls")
  @Operation(summary = "Delete all comic crawls", description = "Delete all comic crawls (soft delete by default, hard delete if specified)")
  public ResponseEntity<ApiResponse<Object>> deleteAllComicCrawls(
      @RequestParam(defaultValue = "false") boolean hardDelete,
      @RequestParam(required = false) ComicCrawlStatus status,
      @AuthenticationPrincipal UserPrincipal userPrincipal) {
    try {
      if (hardDelete) {
        comicCrawlService.deleteAllCrawlsHard(status);
        return ok(success("Tất cả crawls đã được xóa vĩnh viễn"));
      } else {
        comicCrawlService.deleteAllCrawlsSoft(userPrincipal.getId(), status);
        return ok(success("Tất cả crawls đã được xóa (có thể khôi phục)"));
      }
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(ApiResponse.error("Lỗi khi xóa crawls: " + e.getMessage()));
    }
  }
}
