package com.truyengg.service;

import com.truyengg.domain.entity.Comic;
import com.truyengg.domain.repository.ComicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.data.domain.Page.empty;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComicSearchService {

  private static final double FUZZY_THRESHOLD = 0.3;

  private final ComicRepository comicRepository;

  @Transactional(readOnly = true)
  @Cacheable(value = "comicSearch", key = "#query + '-' + #pageable.pageNumber + '-' + #pageable.pageSize", unless = "#result.isEmpty()")
  public Page<Comic> fuzzySearch(String query, Pageable pageable) {
    if (isBlank(query)) {
      return empty(pageable);
    }

    var trimmedQuery = query.trim().toLowerCase();

    var fulltextResults = comicRepository.searchByFulltext(trimmedQuery, pageable);

    if (fulltextResults.isEmpty() || fulltextResults.getContent().size() < pageable.getPageSize()) {
      var fuzzyResults = comicRepository.searchByFuzzySimilarity(trimmedQuery, FUZZY_THRESHOLD, pageable);

      return combineResults(fulltextResults, fuzzyResults, pageable);
    }

    return fulltextResults;
  }

  private Page<Comic> combineResults(Page<Comic> fulltextResults, Page<Comic> fuzzyResults, Pageable pageable) {
    var combined = new HashSet<Comic>();
    combined.addAll(fulltextResults.getContent());
    combined.addAll(fuzzyResults.getContent());

    var combinedList = combined.stream()
        .limit(pageable.getPageSize())
        .toList();

    return new PageImpl<>(combinedList, pageable, combined.size());
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "comicSearchByFulltext", key = "#query + '-' + #pageable.pageNumber + '-' + #pageable.pageSize", unless = "#result.isEmpty()")
  public Page<Comic> searchByFulltext(String query, Pageable pageable) {
    return comicRepository.searchByFulltext(query, pageable);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "comicSearchByFuzzy", key = "#query + '-' + #threshold + '-' + #pageable.pageNumber + '-' + #pageable.pageSize", unless = "#result.isEmpty()")
  public Page<Comic> searchByFuzzy(String query, double threshold, Pageable pageable) {
    return comicRepository.searchByFuzzySimilarity(query, threshold, pageable);
  }

  @Transactional
  public void refreshSearchCache() {
    comicRepository.refreshComicsSearchCache();
  }
}

