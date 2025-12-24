package com.truyengg.model.request;

import jakarta.validation.constraints.Min;

public record AdvancedSearchRequest(
    String keywords,
    String genres,
    String notGenres,
    String country,
    String status,
    @Min(0) Integer minChapter,
    String sort,
    @Min(1) Integer page,
    @Min(1) Integer limit
) {
  public AdvancedSearchRequest {
    if (page == null) page = 1;
    if (limit == null) limit = 24;
    if (minChapter == null) minChapter = 0;
  }
}
