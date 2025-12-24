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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Search", description = "Search and advanced search APIs")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

  private final ComicService comicService;

  @GetMapping
  public ResponseEntity<ApiResponse<Page<ComicResponse>>> search(
      @RequestParam String query,
      @PageableDefault(size = 24) Pageable pageable) {
    Page<ComicResponse> comics = comicService.searchComics(query, pageable);
    return ResponseEntity.ok(ApiResponse.success(comics));
  }

  @PostMapping("/advanced")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Page<ComicResponse>>> advancedSearch(
      @RequestParam(required = false) String keywords,
      @RequestParam(required = false) String genres,
      @RequestParam(required = false) String notGenres,
      @RequestParam(required = false) String country,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Integer minChapter,
      @RequestParam(required = false) String sort,
      @PageableDefault(size = 24) Pageable pageable) {
    Page<ComicResponse> comics = comicService.advancedSearch(
        keywords, genres, notGenres, country, status, minChapter, sort, pageable);
    return ResponseEntity.ok(ApiResponse.success(comics));
  }
}

