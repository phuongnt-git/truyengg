package com.truyengg.controller.api;

import com.truyengg.domain.entity.Category;
import com.truyengg.domain.repository.CategoryRepository;
import com.truyengg.model.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Categories", description = "Category listing APIs")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

  private final CategoryRepository categoryRepository;

  @GetMapping
  public ResponseEntity<ApiResponse<List<Category>>> getCategories() {
    var categories = categoryRepository.findAllByOrderByNameAsc();
    return ResponseEntity.ok(ApiResponse.success(categories));
  }
}

