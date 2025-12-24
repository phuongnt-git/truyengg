package com.truyengg.controller.api;

import com.truyengg.model.response.ApiResponse;
import com.truyengg.model.response.ChapterResponse;
import com.truyengg.service.ChapterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Chapters", description = "Chapter reading and management APIs")
@RestController
@RequestMapping("/api/chapters")
@RequiredArgsConstructor
public class ChapterController {

  private final ChapterService chapterService;

  @GetMapping("/comic/{comicSlug}")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<List<ChapterResponse>>> getChaptersByComic(
      @PathVariable String comicSlug) {
    List<ChapterResponse> chapters = chapterService.getChaptersByComicSlug(comicSlug);
    return ResponseEntity.ok(ApiResponse.success(chapters));
  }

  @GetMapping("/comic/{comicSlug}/chapter/{chapterName}")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<ChapterResponse>> getChapter(
      @PathVariable String comicSlug,
      @PathVariable String chapterName) {
    ChapterResponse chapter = chapterService.getChapterByComicAndChapterName(comicSlug, chapterName);
    return ResponseEntity.ok(ApiResponse.success(chapter));
  }
}

