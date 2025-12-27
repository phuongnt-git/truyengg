package com.truyengg.controller.api.admin;

import com.truyengg.domain.entity.Comic;
import com.truyengg.domain.enums.ComicProgressStatus;
import com.truyengg.domain.enums.ComicStatus;
import com.truyengg.domain.repository.ComicRepository;
import com.truyengg.model.mapper.ComicMapper;
import com.truyengg.model.response.ApiResponse;
import com.truyengg.model.response.ComicResponse;
import com.truyengg.service.comic.ComicDuplicateService;
import com.truyengg.service.comic.ComicService;
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

@Tag(name = "Admin Comics", description = "Admin comic management APIs")
@RestController
@RequestMapping("/api/admin/comics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminComicController {

  private final ComicService comicService;
  private final ComicDuplicateService comicDuplicateService;
  private final ComicRepository comicRepository;
  private final ComicMapper comicMapper;

  @GetMapping
  @Operation(summary = "Get all comics with filters", description = "Get paginated list of comics with optional filters")
  public ResponseEntity<ApiResponse<Page<ComicResponse>>> getAllComics(
      @RequestParam(required = false) ComicStatus status,
      @RequestParam(required = false) ComicProgressStatus progressStatus,
      @RequestParam(required = false) String search,
      Pageable pageable) {
    Specification<Comic> spec = buildSpecification(status, progressStatus, search);
    var comics = comicRepository.findAll(spec, pageable);
    var responses = comics.map(comic -> {
      var response = comicMapper.toResponse(comic);
      return enrichComicResponse(comic, response);
    });
    return ResponseEntity.ok(ApiResponse.success(responses));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get comic by ID", description = "Get detailed information about a comic")
  public ResponseEntity<ApiResponse<ComicResponse>> getComicById(@PathVariable Long id) {
    var comic = comicService.getComicById(id);
    return ResponseEntity.ok(ApiResponse.success(comic));
  }


  @PutMapping("/{id}/status")
  @Operation(summary = "Update comic status", description = "Update workflow status of a comic")
  public ResponseEntity<ApiResponse<ComicResponse>> updateStatus(
      @PathVariable Long id,
      @RequestParam ComicStatus status) {
    var comic = comicRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Comic not found: " + id));
    comic.setStatus(status);
    comic = comicRepository.save(comic);
    var response = comicMapper.toResponse(comic);
    return ResponseEntity.ok(ApiResponse.success(enrichComicResponse(comic, response)));
  }

  @PutMapping("/{id}/progress-status")
  @Operation(summary = "Update comic progress status", description = "Update progress status of a comic")
  public ResponseEntity<ApiResponse<ComicResponse>> updateProgressStatus(
      @PathVariable Long id,
      @RequestParam ComicProgressStatus progressStatus) {
    var comic = comicRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Comic not found: " + id));
    comic.setProgressStatus(progressStatus);
    comic = comicRepository.save(comic);
    var response = comicMapper.toResponse(comic);
    return ResponseEntity.ok(ApiResponse.success(enrichComicResponse(comic, response)));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete comic", description = "Soft delete a comic by setting status to ARCHIVED")
  public ResponseEntity<ApiResponse<Object>> deleteComic(@PathVariable Long id) {
    var comic = comicRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Comic not found: " + id));
    comic.setStatus(ComicStatus.ARCHIVED);
    comicRepository.save(comic);
    return ResponseEntity.ok(ApiResponse.success("Comic archived successfully"));
  }

  @PostMapping("/bulk/status")
  @Operation(summary = "Bulk update status", description = "Update status for multiple comics")
  public ResponseEntity<ApiResponse<Object>> bulkUpdateStatus(
      @RequestBody List<Long> ids,
      @RequestParam ComicStatus status) {
    var comics = comicRepository.findAllById(ids);
    comics.forEach(comic -> comic.setStatus(status));
    comicRepository.saveAll(comics);
    return ResponseEntity.ok(ApiResponse.success("Updated status for " + comics.size() + " comics"));
  }

  @PostMapping("/bulk/delete")
  @Operation(summary = "Bulk delete comics", description = "Soft delete multiple comics")
  public ResponseEntity<ApiResponse<Object>> bulkDelete(@RequestBody List<Long> ids) {
    var comics = comicRepository.findAllById(ids);
    comics.forEach(comic -> comic.setStatus(ComicStatus.ARCHIVED));
    comicRepository.saveAll(comics);
    return ResponseEntity.ok(ApiResponse.success("Archived " + comics.size() + " comics"));
  }

  @GetMapping("/{id}/duplicates")
  @Operation(summary = "Detect duplicates", description = "Find potential duplicate comics for a given comic")
  public ResponseEntity<ApiResponse<List<DuplicateResponse>>> detectDuplicates(@PathVariable Long id) {
    var comic = comicRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Comic not found: " + id));
    var candidates = comicDuplicateService.detectDuplicates(comic);
    var responses = candidates.stream()
        .map(candidate -> new DuplicateResponse(
            candidate.comic().getId(),
            candidate.comic().getName(),
            candidate.comic().getSlug(),
            candidate.comic().getSource(),
            candidate.similarity()
        ))
        .toList();
    return ResponseEntity.ok(ApiResponse.success(responses));
  }

  @PostMapping("/merge")
  @Operation(summary = "Merge comics", description = "Merge a duplicate comic into a primary comic")
  public ResponseEntity<ApiResponse<ComicResponse>> mergeComics(
      @RequestParam Long primaryId,
      @RequestParam Long duplicateId) {
    var primary = comicRepository.findById(primaryId)
        .orElseThrow(() -> new IllegalArgumentException("Primary comic not found: " + primaryId));
    var duplicate = comicRepository.findById(duplicateId)
        .orElseThrow(() -> new IllegalArgumentException("Duplicate comic not found: " + duplicateId));

    var merged = comicDuplicateService.mergeComics(primary, duplicate, null);
    var response = comicMapper.toResponse(merged);
    return ResponseEntity.ok(ApiResponse.success(enrichComicResponse(merged, response)));
  }

  @GetMapping("/duplicates/pending")
  @Operation(summary = "Get pending duplicates", description = "Get all comics marked as DUPLICATE_DETECTED")
  public ResponseEntity<ApiResponse<Page<ComicResponse>>> getPendingDuplicates(Pageable pageable) {
    Specification<Comic> spec = (root, query, cb) ->
        cb.equal(root.get("status"), ComicStatus.DUPLICATE_DETECTED);
    var comics = comicRepository.findAll(spec, pageable);
    var responses = comics.map(comic -> {
      var response = comicMapper.toResponse(comic);
      return enrichComicResponse(comic, response);
    });
    return ResponseEntity.ok(ApiResponse.success(responses));
  }

  private ComicResponse enrichComicResponse(Comic comic, ComicResponse response) {
    // Use ComicService's enrichComicResponse method which handles chapter count and follow count
    return comicService.getComicById(comic.getId());
  }

  private Specification<Comic> buildSpecification(ComicStatus status, ComicProgressStatus progressStatus, String search) {
    return (root, query, cb) -> {
      var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }

      if (progressStatus != null) {
        predicates.add(cb.equal(root.get("progressStatus"), progressStatus));
      }

      if (search != null && !search.isBlank()) {
        var searchPattern = "%" + search.toLowerCase() + "%";
        predicates.add(cb.or(
            cb.like(cb.lower(root.get("name")), searchPattern),
            cb.like(cb.lower(root.get("originName")), searchPattern),
            cb.like(cb.lower(root.get("author")), searchPattern)
        ));
      }

      return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
    };
  }

  public record DuplicateResponse(
      Long id,
      String name,
      String slug,
      String source,
      double similarity
  ) {
  }
}

