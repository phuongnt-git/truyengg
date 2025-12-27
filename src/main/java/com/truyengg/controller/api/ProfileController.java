package com.truyengg.controller.api;

import com.truyengg.model.request.ChangePasswordRequest;
import com.truyengg.model.request.UpdateProfileRequest;
import com.truyengg.model.response.ApiResponse;
import com.truyengg.model.response.UserResponse;
import com.truyengg.security.UserPrincipal;
import com.truyengg.service.auth.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

@Tag(name = "Profile", description = "User profile management APIs")
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

  private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
      "image/jpeg", "image/png", "image/gif", "image/webp"
  );
  private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

  private final UserService userService;

  @Value("${truyengg.storage.local.base-path:./uploads}")
  private String basePath;

  @PostConstruct
  public void init() {
    try {
      var avatarsDir = Path.of(basePath, "avatars");
      if (!Files.exists(avatarsDir)) {
        Files.createDirectories(avatarsDir);
        log.info("Created avatars directory: {}", avatarsDir);
      }
    } catch (IOException e) {
      log.error("Failed to create avatars directory: {}", e.getMessage(), e);
    }
  }

  @GetMapping
  public ResponseEntity<ApiResponse<UserResponse>> getProfile(
      @AuthenticationPrincipal UserPrincipal userPrincipal) {
    var user = userService.getCurrentUserProfile(userPrincipal.id());
    return ResponseEntity.ok(ApiResponse.success(user));
  }

  @PutMapping
  public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @Valid @RequestBody UpdateProfileRequest request) {
    var user = userService.updateUser(
        userPrincipal.id(),
        request.username(),
        request.firstName(),
        request.lastName(),
        request.avatar()
    );
    return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin thành công", user));
  }

  @PostMapping("/avatar")
  @Operation(summary = "Upload avatar", description = "Upload user avatar image")
  public ResponseEntity<ApiResponse<UserResponse>> uploadAvatar(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @RequestParam("file") MultipartFile file) {

    // Validate file
    if (file.isEmpty()) {
      return ResponseEntity.badRequest()
          .body(ApiResponse.error("File không được để trống"));
    }

    var contentType = file.getContentType();
    if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
      return ResponseEntity.badRequest()
          .body(ApiResponse.error("Chỉ chấp nhận file ảnh (JPEG, PNG, GIF, WebP)"));
    }

    if (file.getSize() > MAX_FILE_SIZE) {
      return ResponseEntity.badRequest()
          .body(ApiResponse.error("File không được vượt quá 5MB"));
    }

    try {
      // Generate unique filename
      var originalFilename = file.getOriginalFilename();
      var extension = getFileExtension(originalFilename);
      var newFilename = UUID.randomUUID() + extension;

      // Save file to local storage
      var avatarsDir = Path.of(basePath, "avatars");
      var filePath = avatarsDir.resolve(newFilename);
      Files.write(filePath, file.getBytes());

      // Generate URL path for the avatar
      var avatarUrl = "/uploads/avatars/" + newFilename;

      // Update user avatar
      userService.updateAvatar(userPrincipal.id(), avatarUrl);

      var user = userService.getCurrentUserProfile(userPrincipal.id());
      return ResponseEntity.ok(ApiResponse.success("Upload avatar thành công", user));
    } catch (IOException e) {
      log.error("Failed to upload avatar for user {}: {}", userPrincipal.id(), e.getMessage(), e);
      return ResponseEntity.internalServerError()
          .body(ApiResponse.error("Lỗi khi upload avatar"));
    }
  }

  private String getFileExtension(String filename) {
    if (filename == null || !filename.contains(".")) {
      return ".jpg";
    }
    return filename.substring(filename.lastIndexOf("."));
  }

  @PostMapping("/password")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Object>> changePassword(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @Valid @RequestBody ChangePasswordRequest request) {
    userService.changePassword(
        userPrincipal.id(),
        request.oldPassword(),
        request.newPassword()
    );
    return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công"));
  }
}
