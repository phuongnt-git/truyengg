package com.truyengg.service;

import com.truyengg.domain.entity.Comic;
import com.truyengg.domain.repository.ComicRepository;
import com.truyengg.model.response.ComicResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

import static com.truyengg.domain.enums.ComicProgressStatus.COMING_SOON;
import static com.truyengg.domain.enums.ComicProgressStatus.COMPLETED;
import static com.truyengg.domain.enums.ComicStatus.ACTIVE;
import static java.time.ZoneId.of;

@Service
@RequiredArgsConstructor
@Slf4j
public class ListService {

  private final ComicRepository comicRepository;

  @Transactional(readOnly = true)
  @Cacheable(value = "comic:new#15m", unless = "#result.isEmpty()")
  public Page<ComicResponse> getNewComics(Pageable pageable) {
    var since = ZonedDateTime.now(of("Asia/Ho_Chi_Minh")).minusDays(7);
    Specification<Comic> spec = (root, query, cb) ->
        cb.greaterThanOrEqualTo(root.get("updatedAt"), since);

    return comicRepository.findAll(spec, pageable)
        .map(ComicResponse::from);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "comic:releases#15m", unless = "#result.isEmpty()")
  public Page<ComicResponse> getNewReleases(Pageable pageable) {
    var since = ZonedDateTime.now(of("Asia/Ho_Chi_Minh")).minusDays(30);
    Specification<Comic> spec = (root, query, cb) ->
        cb.greaterThanOrEqualTo(root.get("createdAt"), since);

    return comicRepository.findAll(spec, pageable)
        .map(ComicResponse::from);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "comic:completed#30m", unless = "#result.isEmpty()")
  public Page<ComicResponse> getCompletedComics(Pageable pageable) {
    Specification<Comic> spec = (root, query, cb) -> cb.and(
        cb.equal(root.get("status"), ACTIVE),
        cb.isNull(root.get("mergedComic")),
        cb.equal(root.get("progressStatus"), COMPLETED)
    );

    return comicRepository.findAll(spec, pageable)
        .map(ComicResponse::from);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "comic:upcoming#30m", unless = "#result.isEmpty()")
  public Page<ComicResponse> getUpcomingComics(Pageable pageable) {
    Specification<Comic> spec = (root, query, cb) -> cb.and(
        cb.equal(root.get("status"), ACTIVE),
        cb.isNull(root.get("mergedComic")),
        cb.equal(root.get("progressStatus"), COMING_SOON)
    );

    return comicRepository.findAll(spec, pageable)
        .map(ComicResponse::from);
  }
}
