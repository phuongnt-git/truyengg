package com.truyengg.service.comic;

import com.truyengg.domain.entity.Comic;
import com.truyengg.domain.repository.ComicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.truyengg.domain.enums.ComicStatus.MERGED;
import static java.lang.Math.max;
import static java.util.Collections.emptyList;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComicDuplicateService {

  private static final double MEDIUM_SIMILARITY_THRESHOLD = 0.7;

  private final ComicRepository comicRepository;

  @Transactional(readOnly = true)
  public List<DuplicateCandidate> detectDuplicates(Comic comic) {
    if (comic == null || isBlank(comic.getName())) {
      return emptyList();
    }

    var candidates = new ArrayList<DuplicateCandidate>();

    // Find potential duplicates by name similarity
    var allComics = comicRepository.findAll();
    for (var otherComic : allComics) {
      if (otherComic.getId().equals(comic.getId())) {
        continue;
      }

      if (otherComic.getStatus() == MERGED) {
        continue;
      }

      var similarity = calculateSimilarity(comic, otherComic);
      if (similarity >= MEDIUM_SIMILARITY_THRESHOLD) {
        candidates.add(new DuplicateCandidate(otherComic, similarity));
      }
    }

    return candidates.stream()
        .sorted(Comparator.comparing(DuplicateCandidate::similarity).reversed())
        .toList();
  }

  private double calculateSimilarity(Comic comic1, Comic comic2) {
    var scores = new ArrayList<Double>();

    // Name similarity
    if (isNotBlank(comic1.getName()) && isNotBlank(comic2.getName())) {
      scores.add(calculateStringSimilarity(comic1.getName(), comic2.getName()));
    }

    // Origin name similarity
    if (isNotBlank(comic1.getOriginName()) && isNotBlank(comic2.getOriginName())) {
      scores.add(calculateStringSimilarity(comic1.getOriginName(), comic2.getOriginName()));
    }

    // Author similarity
    if (isNotBlank(comic1.getAuthor()) && isNotBlank(comic2.getAuthor())) {
      scores.add(calculateStringSimilarity(comic1.getAuthor(), comic2.getAuthor()));
    }

    // Alternative names similarity
    if (isNotEmpty(comic1.getAlternativeNames()) &&
        isNotEmpty(comic2.getAlternativeNames())) {
      var altSimilarity = calculateAlternativeNamesSimilarity(
          comic1.getAlternativeNames(),
          comic2.getAlternativeNames()
      );
      scores.add(altSimilarity);
    }

    // Return average similarity
    if (scores.isEmpty()) {
      return 0.0;
    }

    return scores.stream()
        .mapToDouble(Double::doubleValue)
        .average()
        .orElse(0.0);
  }

  private double calculateStringSimilarity(String str1, String str2) {
    if (str1 == null || str2 == null) {
      return 0.0;
    }

    var s1 = str1.toLowerCase().trim();
    var s2 = str2.toLowerCase().trim();

    if (s1.equals(s2)) {
      return 1.0;
    }

    // Use Jaro-Winkler similarity (simplified version)
    return calculateJaroWinklerSimilarity(s1, s2);
  }

  private double calculateJaroWinklerSimilarity(String s1, String s2) {
    // Simplified Jaro-Winkler implementation
    // For production, consider using a library like Apache Commons Text
    var jaro = calculateJaroSimilarity(s1, s2);
    var prefixLength = getCommonPrefixLength(s1, s2, 5);
    return jaro + (0.1 * prefixLength * (1 - jaro));
  }

  private double calculateJaroSimilarity(String s1, String s2) {
    if (s1.equals(s2)) {
      return 1.0;
    }

    var matchWindow = max(s1.length(), s2.length()) / 2 - 1;
    var s1Matches = new boolean[s1.length()];
    var s2Matches = new boolean[s2.length()];

    var matches = 0;
    var transpositions = 0;

    // Find matches
    for (var i = 0; i < s1.length(); i++) {
      var start = max(0, i - matchWindow);
      var end = Math.min(i + matchWindow + 1, s2.length());

      for (var j = start; j < end; j++) {
        if (s2Matches[j] || s1.charAt(i) != s2.charAt(j)) {
          continue;
        }
        s1Matches[i] = true;
        s2Matches[j] = true;
        matches++;
        break;
      }
    }

    if (matches == 0) {
      return 0.0;
    }

    // Find transpositions
    var k = 0;
    for (var i = 0; i < s1.length(); i++) {
      if (!s1Matches[i]) {
        continue;
      }
      while (!s2Matches[k]) {
        k++;
      }
      if (s1.charAt(i) != s2.charAt(k)) {
        transpositions++;
      }
      k++;
    }

    return (matches / (double) s1.length() +
        matches / (double) s2.length() +
        (matches - transpositions / 2.0) / matches) / 3.0;
  }

  private int getCommonPrefixLength(String s1, String s2, int maxLength) {
    var prefixLength = 0;
    var minLength = Math.min(Math.min(s1.length(), s2.length()), maxLength);
    for (var i = 0; i < minLength; i++) {
      if (s1.charAt(i) == s2.charAt(i)) {
        prefixLength++;
      } else {
        break;
      }
    }
    return prefixLength;
  }

  private double calculateAlternativeNamesSimilarity(List<String> names1, List<String> names2) {
    if (CollectionUtils.isEmpty(names1) || CollectionUtils.isEmpty(names2)) {
      return 0.0;
    }

    var maxSimilarity = 0.0;
    for (var name1 : names1) {
      for (var name2 : names2) {
        var similarity = calculateStringSimilarity(name1, name2);
        maxSimilarity = max(maxSimilarity, similarity);
      }
    }

    return maxSimilarity;
  }

  @Transactional
  public Comic mergeComics(Comic primaryComic, Comic duplicateComic, Long mergedBy) {
    if (isNotEmpty(duplicateComic.getChapters())) {
      for (var chapter : duplicateComic.getChapters()) {
        chapter.setComic(primaryComic);
      }
      primaryComic.getChapters().addAll(duplicateComic.getChapters());
    }

    if (isNotEmpty(duplicateComic.getAlternativeNames())) {
      var mergedNames = new ArrayList<>(primaryComic.getAlternativeNames());
      for (var altName : duplicateComic.getAlternativeNames()) {
        if (!mergedNames.contains(altName)) {
          mergedNames.add(altName);
        }
      }
      primaryComic.setAlternativeNames(mergedNames);
    }

    if (primaryComic.getViews() < duplicateComic.getViews()) {
      primaryComic.setViews(duplicateComic.getViews());
    }
    if (primaryComic.getLikes() < duplicateComic.getLikes()) {
      primaryComic.setLikes(duplicateComic.getLikes());
    }
    if (primaryComic.getFollows() < duplicateComic.getFollows()) {
      primaryComic.setFollows(duplicateComic.getFollows());
    }

    duplicateComic.setStatus(MERGED);
    duplicateComic.setMergedComic(primaryComic);

    comicRepository.save(primaryComic);
    comicRepository.save(duplicateComic);

    return primaryComic;
  }

  public record DuplicateCandidate(Comic comic, double similarity) {
  }
}

