package com.truyengg.controller.api.admin;

import com.truyengg.domain.entity.Comic;
import com.truyengg.domain.enums.ComicStatus;
import com.truyengg.domain.repository.ComicRepository;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin Duplicates", description = "Admin duplicate detection and merge management APIs")
@RestController
@RequestMapping("/api/admin/duplicates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDuplicateController {

  private final ComicDuplicateService comicDuplicateService;
  private final ComicService comicService;
  private final ComicRepository comicRepository;

  @GetMapping("/pending")
  @Operation(summary = "Get pending duplicates", description = "Get all comics marked as DUPLICATE_DETECTED")
  public ResponseEntity<ApiResponse<Page<ComicResponse>>> getPendingDuplicates(Pageable pageable) {
    Specification<Comic> spec = (root, query, cb) ->
        cb.equal(root.get("status"), ComicStatus.DUPLICATE_DETECTED);
    var comics = comicRepository.findAll(spec, pageable);
    var responses = comics.map(comic -> comicService.getComicById(comic.getId()));
    return ResponseEntity.ok(ApiResponse.success(responses));
  }

  @GetMapping("/{id}/candidates")
  @Operation(summary = "Get duplicate candidates", description = "Get potential duplicate comics for a given comic")
  public ResponseEntity<ApiResponse<List<DuplicateCandidateResponse>>> getDuplicateCandidates(@PathVariable Long id) {
    var comic = comicRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Comic not found: " + id));
    var candidates = comicDuplicateService.detectDuplicates(comic);
    var responses = candidates.stream()
        .map(candidate -> new DuplicateCandidateResponse(
            candidate.comic().getId(),
            candidate.comic().getName(),
            candidate.comic().getSlug(),
            candidate.comic().getSource(),
            candidate.comic().getStatus(),
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
    return ResponseEntity.ok(ApiResponse.success(comicService.getComicById(merged.getId())));
  }

  @PostMapping("/{id}/approve")
  @Operation(summary = "Approve duplicate", description = "Mark a comic as approved (not a duplicate)")
  public ResponseEntity<ApiResponse<ComicResponse>> approveDuplicate(@PathVariable Long id) {
    var comic = comicRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Comic not found: " + id));
    comic.setStatus(ComicStatus.ACTIVE);
    comic = comicRepository.save(comic);
    return ResponseEntity.ok(ApiResponse.success(comicService.getComicById(comic.getId())));
  }

  @PostMapping("/{id}/reject")
  @Operation(summary = "Reject duplicate", description = "Mark a comic as rejected (keep as separate)")
  public ResponseEntity<ApiResponse<ComicResponse>> rejectDuplicate(@PathVariable Long id) {
    var comic = comicRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Comic not found: " + id));
    comic.setStatus(ComicStatus.ACTIVE);
    comic = comicRepository.save(comic);
    return ResponseEntity.ok(ApiResponse.success(comicService.getComicById(comic.getId())));
  }

  public record DuplicateCandidateResponse(
      Long id,
      String name,
      String slug,
      String source,
      ComicStatus status,
      double similarity
  ) {
  }
}

