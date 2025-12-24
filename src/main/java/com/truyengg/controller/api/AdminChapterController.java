package com.truyengg.controller.api;

import com.truyengg.domain.entity.Chapter;
import com.truyengg.domain.repository.ChapterRepository;
import com.truyengg.domain.repository.ComicRepository;
import com.truyengg.model.response.ApiResponse;
import com.truyengg.model.response.ChapterResponse;
import com.truyengg.service.ChapterImageService;
import com.truyengg.service.ChapterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin Chapters", description = "Admin chapter management APIs")
@RestController
@RequestMapping("/api/admin/chapters")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminChapterController {

  private final ChapterService chapterService;
  private final ChapterImageService chapterImageService;
  private final ChapterRepository chapterRepository;
  private final ComicRepository comicRepository;

  @GetMapping
  @Operation(summary = "Get all chapters with filters", description = "Get paginated list of chapters with optional filters")
  public ResponseEntity<ApiResponse<Page<ChapterResponse>>> getAllChapters(
      @RequestParam(required = false) Long comicId,
      @RequestParam(required = false) String search,
      Pageable pageable) {
    Specification<Chapter> spec = buildSpecification(comicId, search);
    var chapters = chapterRepository.findAll(spec, pageable);
    var responses = chapters.map(this::toResponse);
    return ResponseEntity.ok(ApiResponse.success(responses));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get chapter by ID", description = "Get detailed information about a chapter")
  public ResponseEntity<ApiResponse<ChapterResponse>> getChapterById(@PathVariable Long id) {
    var chapter = chapterRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + id));
    return ResponseEntity.ok(ApiResponse.success(toResponse(chapter)));
  }

  @GetMapping("/comic/{comicId}")
  @Operation(summary = "Get chapters by comic ID", description = "Get all chapters for a specific comic")
  public ResponseEntity<ApiResponse<List<ChapterResponse>>> getChaptersByComic(@PathVariable Long comicId) {
    var comic = comicRepository.findById(comicId)
        .orElseThrow(() -> new IllegalArgumentException("Comic not found: " + comicId));
    var chapters = chapterRepository.findByComicOrderByCreatedAtAsc(comic);
    var responses = chapters.stream().map(this::toResponse).toList();
    return ResponseEntity.ok(ApiResponse.success(responses));
  }

  @PostMapping("/{id}/restore")
  @Operation(summary = "Restore chapter", description = "Restore a soft-deleted chapter")
  public ResponseEntity<ApiResponse<ChapterResponse>> restoreChapter(@PathVariable Long id) {
    chapterService.restoreChapter(id);
    var chapter = chapterRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + id));
    return ResponseEntity.ok(ApiResponse.success(toResponse(chapter)));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete chapter", description = "Soft delete a chapter")
  public ResponseEntity<ApiResponse<Object>> deleteChapter(@PathVariable Long id) {
    chapterService.softDeleteChapter(id);
    return ResponseEntity.ok(ApiResponse.success("Chapter soft deleted successfully"));
  }

  @DeleteMapping("/{id}/hard")
  @Operation(summary = "Hard delete chapter", description = "Permanently delete a chapter and all its images")
  public ResponseEntity<ApiResponse<Object>> hardDeleteChapter(@PathVariable Long id) {
    chapterService.hardDeleteChapter(id);
    return ResponseEntity.ok(ApiResponse.success("Chapter permanently deleted"));
  }

  @PostMapping("/bulk/restore")
  @Operation(summary = "Bulk restore chapters", description = "Restore multiple soft-deleted chapters")
  public ResponseEntity<ApiResponse<Object>> bulkRestoreChapters(@RequestBody List<Long> ids) {
    chapterService.bulkRestoreChapters(ids);
    return ResponseEntity.ok(ApiResponse.success("Restored " + ids.size() + " chapters"));
  }

  @PostMapping("/bulk/delete")
  @Operation(summary = "Bulk soft delete chapters", description = "Soft delete multiple chapters")
  public ResponseEntity<ApiResponse<Object>> bulkDeleteChapters(@RequestBody List<Long> ids) {
    chapterService.bulkSoftDeleteChapters(ids);
    return ResponseEntity.ok(ApiResponse.success("Soft deleted " + ids.size() + " chapters"));
  }

  @PostMapping("/bulk/hard-delete")
  @Operation(summary = "Bulk hard delete chapters", description = "Permanently delete multiple chapters")
  public ResponseEntity<ApiResponse<Object>> bulkHardDeleteChapters(@RequestBody List<Long> ids) {
    chapterService.bulkHardDeleteChapters(ids);
    return ResponseEntity.ok(ApiResponse.success("Permanently deleted " + ids.size() + " chapters"));
  }

  @PutMapping("/{id}/images/{imageId}/order")
  @Operation(summary = "Update image order", description = "Update manual order of a chapter image")
  public ResponseEntity<ApiResponse<Object>> updateImageOrder(
      @PathVariable Long id,
      @PathVariable Long imageId,
      @RequestParam Integer manualOrder) {
    chapterImageService.updateImageOrder(imageId, manualOrder);
    return ResponseEntity.ok(ApiResponse.success("Image order updated"));
  }

  @PutMapping("/{id}/images/{imageId}/visibility")
  @Operation(summary = "Update image visibility", description = "Toggle visibility of a chapter image")
  public ResponseEntity<ApiResponse<Object>> updateImageVisibility(
      @PathVariable Long id,
      @PathVariable Long imageId,
      @RequestParam Boolean isVisible) {
    chapterImageService.updateImageVisibility(imageId, isVisible);
    return ResponseEntity.ok(ApiResponse.success("Image visibility updated"));
  }

  private Specification<Chapter> buildSpecification(Long comicId, String search) {
    return (root, query, cb) -> {
      var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

      if (comicId != null) {
        predicates.add(cb.equal(root.get("comic").get("id"), comicId));
      }

      if (search != null && !search.isBlank()) {
        var searchPattern = "%" + search.toLowerCase() + "%";
        predicates.add(cb.or(
            cb.like(cb.lower(root.get("chapterName")), searchPattern),
            cb.like(cb.lower(root.get("chapterTitle")), searchPattern)
        ));
      }

      // Only show non-deleted chapters by default
      predicates.add(cb.isNull(root.get("deletedAt")));

      return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
    };
  }

  private ChapterResponse toResponse(Chapter chapter) {
    return new ChapterResponse(
        chapter.getId(),
        chapter.getComic().getId(),
        chapter.getComic().getSlug(),
        chapter.getChapterName(),
        chapter.getChapterTitle(),
        chapter.getSource(),
        chapter.getCreatedAt()
    );
  }
}

