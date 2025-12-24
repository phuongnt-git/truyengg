package com.truyengg.controller.api;

import com.truyengg.domain.entity.ChapterImage;
import com.truyengg.domain.repository.ChapterImageRepository;
import com.truyengg.model.dto.ChapterImageInfo;
import com.truyengg.model.response.ApiResponse;
import com.truyengg.service.ChapterImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
import java.util.Map;

@Tag(name = "Admin Images", description = "Admin chapter image management APIs")
@RestController
@RequestMapping("/api/admin/images")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminImageController {

  private final ChapterImageService chapterImageService;
  private final ChapterImageRepository chapterImageRepository;

  @GetMapping("/chapter/{chapterId}")
  @Operation(summary = "Get images by chapter ID", description = "Get all images for a specific chapter")
  public ResponseEntity<ApiResponse<List<ChapterImageInfo>>> getImagesByChapter(@PathVariable Long chapterId) {
    var images = chapterImageRepository.findByChapterIdOrderByImageOrderAsc(chapterId);
    var infos = images.stream()
        .map(image -> new ChapterImageInfo(
            image.getId(),
            image.getChapter().getId(),
            image.getPath(),
            image.getOriginalUrl(),
            image.getImageOrder(),
            image.getManualOrder(),
            image.getIsDownloaded(),
            image.getIsVisible(),
            image.getDeletedAt(),
            image.getCreatedAt(),
            image.getUpdatedAt()
        ))
        .toList();
    return ResponseEntity.ok(ApiResponse.success(infos));
  }

  @PutMapping("/{id}/order")
  @Operation(summary = "Update image order", description = "Update manual order of a chapter image")
  public ResponseEntity<ApiResponse<Object>> updateImageOrder(
      @PathVariable Long id,
      @RequestParam Integer manualOrder) {
    chapterImageService.updateImageOrder(id, manualOrder);
    return ResponseEntity.ok(ApiResponse.success("Image order updated"));
  }

  @PutMapping("/{id}/visibility")
  @Operation(summary = "Update image visibility", description = "Toggle visibility of a chapter image")
  public ResponseEntity<ApiResponse<Object>> updateImageVisibility(
      @PathVariable Long id,
      @RequestParam Boolean isVisible) {
    chapterImageService.updateImageVisibility(id, isVisible);
    return ResponseEntity.ok(ApiResponse.success("Image visibility updated"));
  }

  @PostMapping("/{id}/restore")
  @Operation(summary = "Restore image", description = "Restore a soft-deleted image")
  public ResponseEntity<ApiResponse<Object>> restoreImage(@PathVariable Long id) {
    chapterImageService.restoreImage(id);
    return ResponseEntity.ok(ApiResponse.success("Image restored"));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Soft delete image", description = "Soft delete a chapter image")
  public ResponseEntity<ApiResponse<Object>> deleteImage(@PathVariable Long id) {
    chapterImageService.softDeleteImage(id);
    return ResponseEntity.ok(ApiResponse.success("Image soft deleted"));
  }

  @DeleteMapping("/{id}/hard")
  @Operation(summary = "Hard delete image", description = "Permanently delete a chapter image")
  public ResponseEntity<ApiResponse<Object>> hardDeleteImage(@PathVariable Long id) {
    chapterImageService.hardDeleteImage(id);
    return ResponseEntity.ok(ApiResponse.success("Image permanently deleted"));
  }

  @PostMapping("/bulk/restore")
  @Operation(summary = "Bulk restore images", description = "Restore multiple soft-deleted images")
  public ResponseEntity<ApiResponse<Object>> bulkRestoreImages(@RequestBody List<Long> ids) {
    chapterImageService.bulkRestoreImages(ids);
    return ResponseEntity.ok(ApiResponse.success("Restored " + ids.size() + " images"));
  }

  @PostMapping("/bulk/delete")
  @Operation(summary = "Bulk soft delete images", description = "Soft delete multiple images")
  public ResponseEntity<ApiResponse<Object>> bulkDeleteImages(@RequestBody List<Long> ids) {
    chapterImageService.bulkSoftDeleteImages(ids);
    return ResponseEntity.ok(ApiResponse.success("Soft deleted " + ids.size() + " images"));
  }

  @PostMapping("/bulk/hard-delete")
  @Operation(summary = "Bulk hard delete images", description = "Permanently delete multiple images")
  public ResponseEntity<ApiResponse<Object>> bulkHardDeleteImages(@RequestBody List<Long> ids) {
    chapterImageService.bulkHardDeleteImages(ids);
    return ResponseEntity.ok(ApiResponse.success("Permanently deleted " + ids.size() + " images"));
  }

  @GetMapping("/stats")
  @Operation(summary = "Get image statistics", description = "Get statistics about chapter images")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getImageStats() {
    var totalImages = chapterImageRepository.count();
    var downloadedImages = chapterImageRepository.findAll().stream()
        .filter(ChapterImage::getIsDownloaded)
        .count();
    var visibleImages = chapterImageRepository.findAll().stream()
        .filter(image -> image.getIsVisible() && image.getDeletedAt() == null)
        .count();

    var stats = Map.<String, Object>of(
        "totalImages", totalImages,
        "downloadedImages", downloadedImages,
        "visibleImages", visibleImages,
        "notDownloadedImages", totalImages - downloadedImages
    );
    return ResponseEntity.ok(ApiResponse.success(stats));
  }
}

