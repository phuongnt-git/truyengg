package com.truyengg.controller.api;

import com.truyengg.model.response.ApiResponse;
import com.truyengg.model.response.ComicResponse;
import com.truyengg.service.ComicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Comics", description = "Comic CRUD and listing APIs")
@RestController
@RequestMapping("/api/comics")
@RequiredArgsConstructor
public class ComicController {

  private final ComicService comicService;

  @GetMapping
  public ResponseEntity<ApiResponse<Page<ComicResponse>>> getHomeComics(
      @PageableDefault(size = 24) Pageable pageable) {
    Page<ComicResponse> comics = comicService.getHomeComics(pageable);
    return ResponseEntity.ok(ApiResponse.success(comics));
  }

  @GetMapping("/{slug}")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<ComicResponse>> getComicBySlug(@PathVariable String slug) {
    ComicResponse comic = comicService.getComicBySlug(slug);
    comicService.incrementViews(comic.id());
    return ResponseEntity.ok(ApiResponse.success(comic));
  }

  @GetMapping("/id/{id}")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<ComicResponse>> getComicById(@PathVariable Long id) {
    ComicResponse comic = comicService.getComicById(id);
    return ResponseEntity.ok(ApiResponse.success(comic));
  }

  @GetMapping("/category/{categorySlug}")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Page<ComicResponse>>> getComicsByCategory(
      @PathVariable String categorySlug,
      @PageableDefault(size = 24) Pageable pageable) {
    Page<ComicResponse> comics = comicService.getComicsByCategory(categorySlug, pageable);
    return ResponseEntity.ok(ApiResponse.success(comics));
  }

  @GetMapping("/list/{type}")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Page<ComicResponse>>> getComicList(
      @PathVariable String type,
      @PageableDefault(size = 24) Pageable pageable) {
    Page<ComicResponse> comics = comicService.getComicList(type, pageable);
    return ResponseEntity.ok(ApiResponse.success(comics));
  }
}

