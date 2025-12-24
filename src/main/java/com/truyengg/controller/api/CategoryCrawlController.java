package com.truyengg.controller.api;

import com.truyengg.domain.enums.CategoryCrawlJobStatus;
import com.truyengg.domain.repository.CategoryCrawlDetailRepository;
import com.truyengg.domain.repository.CategoryCrawlJobRepository;
import com.truyengg.domain.repository.ComicCrawlRepository;
import com.truyengg.model.request.CategoryCrawlRequest;
import com.truyengg.model.response.ApiResponse;
import com.truyengg.model.response.CategoryCrawlDetailResponse;
import com.truyengg.model.response.CategoryCrawlJobResponse;
import com.truyengg.model.response.CategoryCrawlProgressResponse;
import com.truyengg.model.response.ComicCrawlResponse;
import com.truyengg.security.UserPrincipal;
import com.truyengg.service.UserService;
import com.truyengg.service.crawl.CategoryCrawlProgressService;
import com.truyengg.service.crawl.CategoryCrawlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag(name = "Category Crawl", description = "Category crawl APIs")
@RestController
@RequestMapping("/api/admin/category-crawl")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class CategoryCrawlController {

  private final CategoryCrawlService categoryCrawlService;
  private final CategoryCrawlJobRepository categoryCrawlJobRepository;
  private final CategoryCrawlDetailRepository categoryCrawlDetailRepository;
  private final CategoryCrawlProgressService categoryCrawlProgressService;
  private final ComicCrawlRepository comicCrawlRepository;
  private final UserService userService;

  @PostMapping("/start")
  @Operation(summary = "Start category crawl", description = "Start crawling all stories from a category")
  public ResponseEntity<ApiResponse<CategoryCrawlJobResponse>> startCategoryCrawl(
      @Valid @RequestBody CategoryCrawlRequest request,
      @AuthenticationPrincipal UserPrincipal userPrincipal) {
    var job = categoryCrawlService.startCategoryCrawl(
        request.categoryUrl(),
        request.source(),
        userPrincipal.getId()
    );
    var username = userService.getCurrentUserProfile(userPrincipal.getId()).username();
    var response = CategoryCrawlJobResponse.fromEntity(job, username);

    if (job.getStatus() == com.truyengg.domain.enums.CategoryCrawlJobStatus.PENDING) {
      return ResponseEntity.ok(ApiResponse.success("Job created and queued. Will start automatically when slot becomes available.", response));
    }

    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/jobs")
  @Operation(summary = "Get category crawl jobs", description = "Get paginated list of category crawl jobs")
  public ResponseEntity<ApiResponse<Page<CategoryCrawlJobResponse>>> getJobs(
      @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    var jobsPage = categoryCrawlJobRepository.findAll(pageable);
    var responsePage = jobsPage.map(job -> {
      var username = userService.getCurrentUserProfile(job.getCreatedBy().getId()).username();
      return CategoryCrawlJobResponse.fromEntity(job, username);
    });
    return ResponseEntity.ok(ApiResponse.success(responsePage));
  }

  @GetMapping("/jobs/{jobId}")
  @Operation(summary = "Get category crawl job details", description = "Get detailed information about a category crawl job")
  public ResponseEntity<ApiResponse<CategoryCrawlJobResponse>> getJobDetail(@PathVariable UUID jobId) {
    var jobOpt = categoryCrawlJobRepository.findById(jobId);
    if (jobOpt.isEmpty()) {
      return ResponseEntity.ok(ApiResponse.error("Category crawl job not found"));
    }

    var job = jobOpt.get();
    var username = userService.getCurrentUserProfile(job.getCreatedBy().getId()).username();
    var response = CategoryCrawlJobResponse.fromEntity(job, username);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/jobs/{jobId}/details")
  @Operation(summary = "Get story details", description = "Get list of story details for a category crawl job")
  public ResponseEntity<ApiResponse<List<CategoryCrawlDetailResponse>>> getStoryDetails(@PathVariable UUID jobId) {
    var details = categoryCrawlDetailRepository.findByCategoryCrawlJob_Id(jobId);
    var responses = details.stream()
        .map(CategoryCrawlDetailResponse::fromEntity)
        .collect(Collectors.toList());
    return ResponseEntity.ok(ApiResponse.success(responses));
  }

  @GetMapping("/jobs/{jobId}/progress")
  @Operation(summary = "Get progress", description = "Get real-time progress for a category crawl job")
  public ResponseEntity<ApiResponse<CategoryCrawlProgressResponse>> getProgress(@PathVariable UUID jobId) {
    var jobOpt = categoryCrawlJobRepository.findById(jobId);
    if (jobOpt.isEmpty()) {
      return ResponseEntity.ok(ApiResponse.error("Category crawl job not found"));
    }

    var job = jobOpt.get();
    var response = categoryCrawlProgressService.getProgressResponse(jobId, job);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/jobs/{jobId}/failed-jobs")
  @Operation(summary = "Get failed crawls", description = "Get list of failed crawls from this category crawl")
  public ResponseEntity<ApiResponse<List<ComicCrawlResponse>>> getFailedCrawls(
      @PathVariable UUID jobId) {
    // Find crawls that reference this category crawl job in their message field
    var allCrawls = comicCrawlRepository.findAll();
    var failedCrawls = allCrawls.stream()
        .filter(crawl -> crawl.getMessage() != null && crawl.getMessage().contains(jobId.toString()))
        .filter(crawl -> crawl.getStatus().name().equals("FAILED"))
        .map(crawl -> {
          var username = userService.getCurrentUserProfile(crawl.getCreatedBy().getId()).username();
          return ComicCrawlResponse.fromEntity(crawl, username);
        })
        .collect(Collectors.toList());

    return ResponseEntity.ok(ApiResponse.success(failedCrawls));
  }

  @PostMapping("/jobs/{jobId}/pause")
  @Operation(summary = "Pause category crawl job")
  public ResponseEntity<ApiResponse<Object>> pauseJob(@PathVariable UUID jobId) {
    var jobOpt = categoryCrawlJobRepository.findById(jobId);
    if (jobOpt.isEmpty()) {
      return ResponseEntity.ok(ApiResponse.error("Job not found"));
    }

    var job = jobOpt.get();
    if (job.getStatus() != CategoryCrawlJobStatus.RUNNING) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(ApiResponse.error("Only running jobs can be paused"));
    }

    job.setStatus(CategoryCrawlJobStatus.PAUSED);
    categoryCrawlJobRepository.save(job);

    return ResponseEntity.ok(ApiResponse.success("Job paused"));
  }

  @PostMapping("/jobs/{jobId}/resume")
  @Operation(summary = "Resume category crawl job")
  public ResponseEntity<ApiResponse<Object>> resumeJob(@PathVariable UUID jobId) {
    var jobOpt = categoryCrawlJobRepository.findById(jobId);
    if (jobOpt.isEmpty()) {
      return ResponseEntity.ok(ApiResponse.error("Job not found"));
    }

    var job = jobOpt.get();
    if (job.getStatus() != CategoryCrawlJobStatus.PAUSED) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(ApiResponse.error("Only paused jobs can be resumed"));
    }

    job.setStatus(CategoryCrawlJobStatus.RUNNING);
    categoryCrawlJobRepository.save(job);

    return ResponseEntity.ok(ApiResponse.success("Job resumed"));
  }

  @PostMapping("/jobs/{jobId}/cancel")
  @Operation(summary = "Cancel category crawl job")
  public ResponseEntity<ApiResponse<Object>> cancelJob(@PathVariable UUID jobId) {
    var jobOpt = categoryCrawlJobRepository.findById(jobId);
    if (jobOpt.isEmpty()) {
      return ResponseEntity.ok(ApiResponse.error("Job not found"));
    }

    var job = jobOpt.get();
    job.setStatus(CategoryCrawlJobStatus.CANCELLED);
    categoryCrawlJobRepository.save(job);

    return ResponseEntity.ok(ApiResponse.success("Job cancelled"));
  }
}

