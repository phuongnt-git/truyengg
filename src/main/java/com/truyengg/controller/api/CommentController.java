package com.truyengg.controller.api;

import com.truyengg.model.request.CommentRequest;
import com.truyengg.model.response.ApiResponse;
import com.truyengg.model.response.CommentResponse;
import com.truyengg.security.UserPrincipal;
import com.truyengg.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Comments", description = "Comment CRUD APIs")
@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

  private final CommentService commentService;

  @GetMapping("/comic/{comicId}")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<List<CommentResponse>>> getCommentsByComic(
      @PathVariable Long comicId) {
    List<CommentResponse> comments = commentService.getCommentsByComicId(comicId);
    return ResponseEntity.ok(ApiResponse.success(comments));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<CommentResponse>> createComment(
      @Valid @RequestBody CommentRequest request,
      @AuthenticationPrincipal UserPrincipal userPrincipal) {
    CommentResponse comment = commentService.createComment(userPrincipal.getId(), request);
    long totalComments = commentService.countCommentsByComicId(request.comicId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("Bình luận đã được gửi", comment));
  }

  @DeleteMapping("/{commentId}")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Object>> deleteComment(
      @PathVariable Long commentId,
      @AuthenticationPrincipal UserPrincipal userPrincipal) {
    commentService.deleteComment(commentId, userPrincipal.getId());
    return ResponseEntity.ok(ApiResponse.success("Bình luận đã được xóa"));
  }

  @GetMapping("/comic/{comicId}/count")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Map<String, Long>>> getCommentCount(@PathVariable Long comicId) {
    long count = commentService.countCommentsByComicId(comicId);
    return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
  }
}

