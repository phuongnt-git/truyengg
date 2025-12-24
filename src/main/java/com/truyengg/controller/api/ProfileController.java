package com.truyengg.controller.api;

import com.truyengg.model.request.ChangePasswordRequest;
import com.truyengg.model.request.UpdateProfileRequest;
import com.truyengg.model.response.ApiResponse;
import com.truyengg.model.response.UserResponse;
import com.truyengg.security.UserPrincipal;
import com.truyengg.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Profile", description = "User profile management APIs")
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

  private final UserService userService;

  @GetMapping
  public ResponseEntity<ApiResponse<UserResponse>> getProfile(
      @AuthenticationPrincipal UserPrincipal userPrincipal) {
    var user = userService.getCurrentUserProfile(userPrincipal.getId());
    return ResponseEntity.ok(ApiResponse.success(user));
  }

  @PutMapping
  public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @Valid @RequestBody UpdateProfileRequest request) {
    var user = userService.updateUser(
        userPrincipal.getId(),
        request.username(),
        request.firstName(),
        request.lastName(),
        request.avatar()
    );
    return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin thành công", user));
  }

  @PostMapping("/avatar")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<UserResponse>> uploadAvatar(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @RequestParam("file") MultipartFile file) {
    // TODO: Upload to ImgBB and get URL
    // For now, return current user
    var user = userService.getCurrentUserProfile(userPrincipal.getId());
    return ResponseEntity.ok(ApiResponse.success("Upload avatar thành công", user));
  }

  @PostMapping("/password")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Object>> changePassword(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @Valid @RequestBody ChangePasswordRequest request) {
    userService.changePassword(
        userPrincipal.getId(),
        request.oldPassword(),
        request.newPassword()
    );
    return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công"));
  }
}
