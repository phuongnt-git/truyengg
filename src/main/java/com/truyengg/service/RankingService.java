package com.truyengg.service;

import com.truyengg.domain.repository.ComicRepository;
import com.truyengg.model.mapper.ComicMapper;
import com.truyengg.model.response.ComicResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.truyengg.domain.constant.AppConstants.ASIA_HO_CHI_MINH;
import static java.time.ZoneId.of;
import static java.time.ZonedDateTime.now;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RankingService {

  ComicRepository comicRepository;
  ComicMapper comicMapper;

  @Transactional(readOnly = true)
  @Cacheable(value = "ranking:daily#10m", unless = "#result.isEmpty()")
  public Page<ComicResponse> getTopDaily(Pageable pageable) {
    var startOfDay = now(of(ASIA_HO_CHI_MINH))
        .toLocalDate()
        .atStartOfDay(of(ASIA_HO_CHI_MINH));

    var comics = comicRepository.findTopComicsByViewsSince(startOfDay, pageable);
    return comics.map(comicMapper::toResponse);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "ranking:weekly#1h", unless = "#result.isEmpty()")
  public Page<ComicResponse> getTopWeekly(Pageable pageable) {
    var startOfWeek = now(of(ASIA_HO_CHI_MINH))
        .minusWeeks(1);

    var comics = comicRepository.findTopComicsByViewsSince(startOfWeek, pageable);
    return comics.map(comicMapper::toResponse);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "ranking:monthly#6h", unless = "#result.isEmpty()")
  public Page<ComicResponse> getTopMonthly(Pageable pageable) {
    var startOfMonth = now(of(ASIA_HO_CHI_MINH))
        .minusMonths(1);

    var comics = comicRepository.findTopComicsByViewsSince(startOfMonth, pageable);
    return comics.map(comicMapper::toResponse);
  }
}
