package com.truyengg.controller.api;

import com.truyengg.model.response.ApiResponse;
import com.truyengg.service.OTruyenApiService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Categories", description = "Category listing APIs")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

  private final OTruyenApiService otruyenApiService;

  @GetMapping
  public ResponseEntity<ApiResponse<Map<String, Object>>> getCategories() {
    Map<String, Object> categories = otruyenApiService.getCategories();
    return ResponseEntity.ok(ApiResponse.success(categories));
  }
}

