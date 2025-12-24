package com.truyengg.controller.api;

import com.truyengg.domain.enums.UserRole;
import com.truyengg.model.response.ApiResponse;
import com.truyengg.model.response.UserResponse;
import com.truyengg.service.SettingService;
import com.truyengg.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.Map;

import static java.time.ZoneId.of;

@Tag(name = "Admin", description = "Admin management APIs")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

  private final SettingService settingService;
  private final UserService userService;

  // Settings endpoints
  @GetMapping("/settings/{key}")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Map<String, String>>> getSetting(@PathVariable String key) {
    var value = settingService.getSettingValue(key, "");
    return ResponseEntity.ok(ApiResponse.success(Map.of("key", key, "value", value)));
  }

  @PostMapping("/settings")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Object>> saveSetting(
      @RequestParam String key,
      @RequestParam String value,
      @RequestParam(required = false) String description) {
    settingService.saveSetting(key, value, description);
    return ResponseEntity.ok(ApiResponse.success("Setting đã được lưu"));
  }

  @DeleteMapping("/settings/{key}")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Object>> deleteSetting(@PathVariable String key) {
    settingService.deleteSetting(key);
    return ResponseEntity.ok(ApiResponse.success("Setting đã được xóa"));
  }

  // User management endpoints
  @GetMapping("/users")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsers(Pageable pageable) {
    var users = userService.getAllUsers(pageable);
    return ResponseEntity.ok(ApiResponse.success(users));
  }

  @GetMapping("/users/{id}")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
    var user = userService.getUserById(id);
    return ResponseEntity.ok(ApiResponse.success(user));
  }

  @PostMapping("/users/{id}/role")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Object>> updateUserRole(
      @PathVariable Long id,
      @RequestParam UserRole role) {
    userService.updateUserRole(id, role);
    return ResponseEntity.ok(ApiResponse.success("Cập nhật vai trò thành công"));
  }

  @PostMapping("/users/{id}/ban")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Object>> banUser(
      @PathVariable Long id,
      @RequestParam String duration) {
    var bannedUntil = switch (duration) {
      case "1day" -> ZonedDateTime.now(of("Asia/Ho_Chi_Minh")).plusDays(1);
      case "7days" -> ZonedDateTime.now(of("Asia/Ho_Chi_Minh")).plusDays(7);
      case "30days" -> ZonedDateTime.now(of("Asia/Ho_Chi_Minh")).plusDays(30);
      case "permanent" -> ZonedDateTime.now(of("Asia/Ho_Chi_Minh")).plusYears(100);
      default -> null;
    };

    if (bannedUntil != null) {
      userService.banUser(id, bannedUntil);
      return ResponseEntity.ok(ApiResponse.success("Đã cấm user thành công"));
    }
    return ResponseEntity.badRequest().body(ApiResponse.error("Duration không hợp lệ"));
  }

  @PostMapping("/users/{id}/unban")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Object>> unbanUser(@PathVariable Long id) {
    userService.unbanUser(id);
    return ResponseEntity.ok(ApiResponse.success("Đã bỏ cấm user thành công"));
  }

  @DeleteMapping("/users/{id}")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Object>> deleteUser(@PathVariable Long id) {
    userService.deleteUser(id);
    return ResponseEntity.ok(ApiResponse.success("Đã xóa user thành công"));
  }

}
