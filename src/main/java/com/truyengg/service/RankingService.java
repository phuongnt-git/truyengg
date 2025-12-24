package com.truyengg.service;

import com.truyengg.domain.repository.ComicRepository;
import com.truyengg.model.mapper.ComicMapper;
import com.truyengg.model.response.ComicResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

import static java.time.ZoneId.of;

@Service
@RequiredArgsConstructor
@Slf4j
public class RankingService {

  public static final String ASIA_HO_CHI_MINH = "Asia/Ho_Chi_Minh";
  private final ComicRepository comicRepository;
  private final ComicMapper comicMapper;

  @Transactional(readOnly = true)
  @Cacheable(value = "topDaily", unless = "#result.isEmpty()")
  public Page<ComicResponse> getTopDaily(Pageable pageable) {
    var startOfDay = ZonedDateTime.now(of(ASIA_HO_CHI_MINH))
        .toLocalDate()
        .atStartOfDay(of(ASIA_HO_CHI_MINH));

    var comics = comicRepository.findTopComicsByViewsSince(startOfDay, pageable);
    return comics.map(comicMapper::toResponse);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "topWeekly", unless = "#result.isEmpty()")
  public Page<ComicResponse> getTopWeekly(Pageable pageable) {
    var startOfWeek = ZonedDateTime.now(of(ASIA_HO_CHI_MINH))
        .minusWeeks(1);

    var comics = comicRepository.findTopComicsByViewsSince(startOfWeek, pageable);
    return comics.map(comicMapper::toResponse);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "topMonthly", unless = "#result.isEmpty()")
  public Page<ComicResponse> getTopMonthly(Pageable pageable) {
    var startOfMonth = ZonedDateTime.now(of(ASIA_HO_CHI_MINH))
        .minusMonths(1);

    var comics = comicRepository.findTopComicsByViewsSince(startOfMonth, pageable);
    return comics.map(comicMapper::toResponse);
  }
}
