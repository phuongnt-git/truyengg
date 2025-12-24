package com.truyengg.model.request;

import java.util.List;

public record SearchRequest(
    String keywords,
    List<String> genres,
    List<String> notGenres,
    String country,
    String status,
    Integer minChapter,
    String sort,
    Integer page,
    Integer limit
) {
  public SearchRequest {
    if (page == null || page < 1) {
      page = 1;
    }
    if (limit == null || limit < 1) {
      limit = 24;
    }
  }
}

