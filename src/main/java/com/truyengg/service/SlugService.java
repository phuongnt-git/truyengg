package com.truyengg.service;

import com.truyengg.domain.repository.ComicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.regex.Pattern;

import static java.util.Locale.ENGLISH;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlugService {

  private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
  private static final Pattern WHITESPACE = Pattern.compile("\\s");
  private static final Pattern EDGES_DASHES = Pattern.compile("(^-|-$)");

  private final ComicRepository comicRepository;

  @Transactional(readOnly = true)
  public String generateSlug(String name) {
    if (isBlank(name)) {
      throw new IllegalArgumentException("Name cannot be blank");
    }

    var normalized = normalizeVietnamese(name);
    var slug = createSlug(normalized);
    return ensureUniqueness(slug);
  }

  private String normalizeVietnamese(String text) {
    var normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
    normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", EMPTY);
    return normalized;
  }

  private String createSlug(String input) {
    var slug = input.toLowerCase(ENGLISH);
    slug = WHITESPACE.matcher(slug).replaceAll("-");
    slug = NON_LATIN.matcher(slug).replaceAll(EMPTY);
    slug = EDGES_DASHES.matcher(slug).replaceAll(EMPTY);
    slug = slug.replaceAll("-+", "-");
    return slug;
  }

  private String ensureUniqueness(String baseSlug) {
    var slug = baseSlug;
    var counter = 1;

    while (comicRepository.existsBySlug(slug)) {
      slug = baseSlug + "-" + counter;
      counter++;
    }

    return slug;
  }

  @Transactional(readOnly = true)
  public boolean isSlugUnique(String slug) {
    return !comicRepository.existsBySlug(slug);
  }
}

