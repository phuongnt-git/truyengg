package com.truyengg.controller.api;

import com.truyengg.model.response.ApiResponse;
import com.truyengg.model.response.ComicResponse;
import com.truyengg.security.UserPrincipal;
import com.truyengg.service.HistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "History", description = "Reading history APIs")
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

  private final HistoryService historyService;

  @PostMapping("/save")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Object>> saveHistory(
      @RequestParam Long comicId,
      @RequestParam(required = false) Long chapterId,
      @RequestParam String chapterName,
      @AuthenticationPrincipal UserPrincipal userPrincipal) {
    historyService.saveReadingHistory(userPrincipal.id(), comicId, chapterId, chapterName);
    return ResponseEntity.ok(ApiResponse.success("Lịch sử đã được lưu"));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<Page<ComicResponse>>> getHistory(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @PageableDefault(size = 24) Pageable pageable) {
    Page<ComicResponse> history = historyService.getReadingHistory(userPrincipal.id(), pageable);
    return ResponseEntity.ok(ApiResponse.success(history));
  }

  @DeleteMapping
  public ResponseEntity<ApiResponse<Object>> clearHistory(
      @AuthenticationPrincipal UserPrincipal userPrincipal) {
    historyService.clearHistory(userPrincipal.id());
    return ResponseEntity.ok(ApiResponse.success("Lịch sử đã được xóa"));
  }
}

