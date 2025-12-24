package com.truyengg.controller.api;

import com.truyengg.model.response.ApiResponse;
import com.truyengg.model.response.ComicResponse;
import com.truyengg.security.UserPrincipal;
import com.truyengg.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Follow", description = "Follow/unfollow comic APIs")
@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowController {

  private final FollowService followService;

  @PostMapping("/comic/{comicId}")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Map<String, Object>>> toggleFollow(
      @PathVariable Long comicId,
      @AuthenticationPrincipal UserPrincipal userPrincipal) {
    boolean isFollowing = followService.toggleFollow(userPrincipal.getId(), comicId);
    long followCount = followService.getFollowCount(comicId);
    return ResponseEntity.ok(ApiResponse.success(Map.of(
        "isFollowing", isFollowing,
        "followCount", followCount,
        "message", isFollowing ? "Đã theo dõi" : "Đã hủy theo dõi"
    )));
  }

  @GetMapping("/comic/{comicId}")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Map<String, Object>>> checkFollow(
      @PathVariable Long comicId,
      @AuthenticationPrincipal UserPrincipal userPrincipal) {
    boolean isFollowing = followService.isFollowing(userPrincipal.getId(), comicId);
    long followCount = followService.getFollowCount(comicId);
    return ResponseEntity.ok(ApiResponse.success(Map.of(
        "isFollowing", isFollowing,
        "followCount", followCount
    )));
  }

  @GetMapping("/my-follows")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Page<ComicResponse>>> getMyFollows(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @PageableDefault(size = 24) Pageable pageable) {
    Page<ComicResponse> comics = followService.getFollowedComics(userPrincipal.getId(), pageable);
    return ResponseEntity.ok(ApiResponse.success(comics));
  }
}

