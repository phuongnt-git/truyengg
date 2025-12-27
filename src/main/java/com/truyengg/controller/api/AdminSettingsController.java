package com.truyengg.controller.api;

import com.truyengg.model.response.ApiResponse;
import com.truyengg.service.SettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Admin Settings", description = "Hierarchical settings management")
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettingsController {

  private final SettingService settingService;

  @GetMapping("/tree")
  @Operation(summary = "Get category tree", description = "Get hierarchical category tree with all settings")
  public ResponseEntity<ApiResponse<Object>> getCategoryTree() {
    var tree = settingService.getCategoryTree();
    return ResponseEntity.ok(ApiResponse.success(tree));
  }

  @GetMapping("/search")
  @Operation(summary = "Search settings", description = "Fuzzy search settings by key or description")
  public ResponseEntity<ApiResponse<Object>> searchSettings(@RequestParam String query) {
    var results = settingService.fuzzySearch(query);
    return ResponseEntity.ok(ApiResponse.success(results));
  }

  @PutMapping
  @Operation(summary = "Update setting", description = "Update a setting value")
  public ResponseEntity<ApiResponse<Object>> updateSetting(
      @RequestBody Map<String, String> request,
      Authentication authentication) {

    var fullKey = request.get("fullKey");
    var value = request.get("value");
    var userId = Long.parseLong(authentication.getName());

    var updated = settingService.updateSetting(fullKey, value, userId);

    return ResponseEntity.ok(ApiResponse.success(updated));
  }
}

