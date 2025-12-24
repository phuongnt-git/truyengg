package com.truyengg.controller.api;

import com.truyengg.model.response.ApiResponse;
import com.truyengg.model.response.ComicResponse;
import com.truyengg.service.RankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Rankings", description = "Top comics ranking APIs")
@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
public class RankingController {

  private final RankingService rankingService;

  @GetMapping("/daily")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Page<ComicResponse>>> getTopDaily(
      @PageableDefault(size = 24) Pageable pageable) {
    var comics = rankingService.getTopDaily(pageable);
    return ResponseEntity.ok(ApiResponse.success(comics));
  }

  @GetMapping("/weekly")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Page<ComicResponse>>> getTopWeekly(
      @PageableDefault(size = 24) Pageable pageable) {
    var comics = rankingService.getTopWeekly(pageable);
    return ResponseEntity.ok(ApiResponse.success(comics));
  }

  @GetMapping("/monthly")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Page<ComicResponse>>> getTopMonthly(
      @PageableDefault(size = 24) Pageable pageable) {
    var comics = rankingService.getTopMonthly(pageable);
    return ResponseEntity.ok(ApiResponse.success(comics));
  }
}
