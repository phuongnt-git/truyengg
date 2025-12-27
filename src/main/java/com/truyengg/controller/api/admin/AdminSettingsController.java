package com.truyengg.controller.api.admin;

import com.truyengg.model.dto.SettingCategoryResponse;
import com.truyengg.model.dto.SettingResponse;
import com.truyengg.model.dto.SettingListItem;
import com.truyengg.model.dto.ValidatedSettingResult;
import com.truyengg.model.request.SettingUpdateRequest;
import com.truyengg.model.response.ApiResponse;
import com.truyengg.security.UserPrincipal;
import com.truyengg.service.SettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.truyengg.model.response.ApiResponse.success;
import static org.springframework.data.domain.PageRequest.of;
import static org.springframework.data.domain.Sort.by;
import static org.springframework.http.ResponseEntity.ok;


@Tag(name = "Admin Settings", description = "Hierarchical settings management")
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettingsController {

  private final SettingService settingService;

  @GetMapping("/categories")
  @Operation(summary = "Get category tree with counts", description = "Get category tree with setting counts and totals")
  public ResponseEntity<ApiResponse<SettingCategoryResponse>> getCategories() {
    var tree = settingService.getCategoryTree();
    return ok(success(tree));
  }

  @GetMapping("/list")
  @Operation(summary = "Get settings paginated", description = "Get settings with pagination, sorting, and optional category filter")
  public ResponseEntity<ApiResponse<Page<SettingListItem>>> getSettings(
      @RequestParam(required = false) Integer categoryId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "fullKey") String sortBy,
      @RequestParam(defaultValue = "asc") String sortDir) {
    var sort = sortDir.equalsIgnoreCase("desc")
        ? by(sortBy).descending()
        : by(sortBy).ascending();
    var pageable = of(page, size, sort);

    var settings = categoryId != null
        ? settingService.getSettingsByCategoryPaginated(categoryId, pageable)
        : settingService.getSettingsPaginated(pageable);

    return ok(success(settings));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get setting by ID", description = "Get single setting detail by ID")
  public ResponseEntity<ApiResponse<SettingResponse>> getSettingById(@PathVariable Long id) {
    var setting = settingService.getSettingById(id);
    return ok(success(setting));
  }

  @GetMapping("/by-key")
  @Operation(summary = "Get setting by full key", description = "Get single setting detail by full key")
  public ResponseEntity<ApiResponse<SettingResponse>> getSettingByFullKey(@RequestParam String fullKey) {
    var setting = settingService.getSettingByFullKey(fullKey);
    return ok(success(setting));
  }

  @GetMapping("/search")
  @Operation(summary = "Search settings", description = "Fuzzy search settings by key or description with pagination")
  public ResponseEntity<ApiResponse<Page<SettingListItem>>> searchSettings(
      @RequestParam String query,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var pageable = of(page, size);
    var results = settingService.searchSettingsPaginated(query, pageable);
    return ok(success(results));
  }

  @PostMapping("/validate")
  @Operation(summary = "Validate setting value", description = "Validate a value against setting constraints")
  public ResponseEntity<ApiResponse<ValidatedSettingResult>> validateSetting(
      @RequestBody Map<String, String> request) {
    var fullKey = request.get("fullKey");
    var value = request.get("value");
    var result = settingService.validateConstraints(fullKey, value);
    return ok(success(result));
  }

  @PutMapping
  @Operation(summary = "Update setting", description = "Update a setting value")
  public ResponseEntity<ApiResponse<SettingResponse>> updateSetting(
      @Valid @RequestBody SettingUpdateRequest request,
      @AuthenticationPrincipal UserPrincipal userPrincipal) {
    settingService.updateSetting(request.fullKey(), request.value(), userPrincipal.id());
    var updated = settingService.getSettingByFullKey(request.fullKey());
    return ok(success(updated));
  }
}

