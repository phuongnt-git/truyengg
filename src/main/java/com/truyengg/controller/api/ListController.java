package com.truyengg.controller.api;

import com.truyengg.model.response.ApiResponse;
import com.truyengg.model.response.ComicResponse;
import com.truyengg.service.ListService;
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

@Tag(name = "Lists", description = "Comic list APIs (new, completed, upcoming)")
@RestController
@RequestMapping("/api/lists")
@RequiredArgsConstructor
public class ListController {

  private final ListService listService;

  @GetMapping("/new-comics")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Page<ComicResponse>>> getNewComics(
      @PageableDefault(size = 24) Pageable pageable) {
    var comics = listService.getNewComics(pageable);
    return ResponseEntity.ok(ApiResponse.success(comics));
  }

  @GetMapping("/new-releases")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Page<ComicResponse>>> getNewReleases(
      @PageableDefault(size = 24) Pageable pageable) {
    var comics = listService.getNewReleases(pageable);
    return ResponseEntity.ok(ApiResponse.success(comics));
  }

  @GetMapping("/completed")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Page<ComicResponse>>> getCompletedComics(
      @PageableDefault(size = 24) Pageable pageable) {
    var comics = listService.getCompletedComics(pageable);
    return ResponseEntity.ok(ApiResponse.success(comics));
  }

  @GetMapping("/upcoming")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Page<ComicResponse>>> getUpcomingComics(
      @PageableDefault(size = 24) Pageable pageable) {
    var comics = listService.getUpcomingComics(pageable);
    return ResponseEntity.ok(ApiResponse.success(comics));
  }
}
