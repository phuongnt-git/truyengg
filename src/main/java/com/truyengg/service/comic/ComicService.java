package com.truyengg.service.comic;

import com.truyengg.domain.entity.Category;
import com.truyengg.domain.entity.Chapter;
import com.truyengg.domain.entity.Comic;
import com.truyengg.domain.enums.ComicProgressStatus;
import com.truyengg.domain.enums.ComicStatus;
import com.truyengg.domain.repository.CategoryRepository;
import com.truyengg.domain.repository.ChapterRepository;
import com.truyengg.domain.repository.ComicRepository;
import com.truyengg.model.dto.ChapterInfo;
import com.truyengg.model.dto.ComicInfo;
import com.truyengg.model.mapper.ComicMapper;
import com.truyengg.model.response.ComicResponse;
import com.truyengg.service.image.ChapterImageService;
import com.truyengg.service.SlugService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.truyengg.domain.enums.ComicStatus.ACTIVE;
import static com.truyengg.domain.enums.ComicStatus.DUPLICATE_DETECTED;
import static java.util.Optional.of;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


@Service
@RequiredArgsConstructor
@Slf4j
public class ComicService {

  private final ComicRepository comicRepository;
  private final ChapterRepository chapterRepository;
  private final CategoryRepository categoryRepository;
  private final ComicMapper comicMapper;
  private final SlugService slugService;
  private final ComicDuplicateService comicDuplicateService;
  private final ChapterImageService chapterImageService;

  @Transactional
  public Optional<Comic> createOrUpdateComic(ComicInfo comicInfo) {
    if (comicInfo == null || isBlank(comicInfo.source())) {
      throw new IllegalArgumentException("ComicInfo and source URL are required");
    }

    var existingOpt = comicRepository.findBySource(comicInfo.source());

    Comic comic;
    if (existingOpt.isPresent()) {
      comic = existingOpt.get();
      updateComicFields(comic, comicInfo);
    } else {
      var slug = isNotBlank(comicInfo.slug())
          ? comicInfo.slug()
          : slugService.generateSlug(comicInfo.name());

      comic = Comic.builder()
          .name(comicInfo.name())
          .slug(slug)
          .originName(comicInfo.originName())
          .content(comicInfo.content())
          .status(ComicStatus.PENDING)
          .progressStatus(comicInfo.progressStatus() != null
              ? comicInfo.progressStatus()
              : ComicProgressStatus.ONGOING)
          .thumbUrl(comicInfo.thumbUrl())
          .author(comicInfo.author())
          .source(comicInfo.source())
          .alternativeNames(comicInfo.alternativeNames())
          .likes(comicInfo.likes() != null ? comicInfo.likes() : 0L)
          .follows(comicInfo.follows() != null ? comicInfo.follows() : 0L)
          .totalChapters(comicInfo.totalChapters() != null ? comicInfo.totalChapters() : 0)
          .lastChapterUpdatedAt(comicInfo.lastChapterUpdatedAt())
          .ageRating(comicInfo.ageRating())
          .gender(comicInfo.gender())
          .country(comicInfo.country())
          .views(0L)
          .isBackedUp(false)
          .isHot(false)
          .build();

      comic = comicRepository.save(comic);

      // Auto-detect duplicates
      var duplicates = comicDuplicateService.detectDuplicates(comic);
      if (!duplicates.isEmpty()) {
        var bestMatch = duplicates.getFirst();
        if (bestMatch.similarity() >= 0.9) {
          // Auto-merge
          log.info("Auto-merging comic {} into {} (similarity: {})",
              comic.getId(), bestMatch.comic().getId(), bestMatch.similarity());
          comic = comicDuplicateService.mergeComics(bestMatch.comic(), comic, null);
        } else if (bestMatch.similarity() >= 0.7) {
          // Mark for review
          comic.setStatus(DUPLICATE_DETECTED);
          comic = comicRepository.save(comic);
          log.info("Marked comic {} as DUPLICATE_DETECTED (similarity: {})",
              comic.getId(), bestMatch.similarity());
        } else {
          // Set as ACTIVE
          comic.setStatus(ACTIVE);
          comic = comicRepository.save(comic);
        }
      } else {
        // No duplicates found, set as ACTIVE
        comic.setStatus(ACTIVE);
        comic = comicRepository.save(comic);
      }
    }

    return of(comic);
  }

  private void updateComicFields(Comic comic, ComicInfo comicInfo) {
    if (isNotBlank(comicInfo.name())) {
      comic.setName(comicInfo.name());
    }
    if (isNotBlank(comicInfo.originName())) {
      comic.setOriginName(comicInfo.originName());
    }
    if (isNotBlank(comicInfo.content())) {
      comic.setContent(comicInfo.content());
    }
    if (comicInfo.progressStatus() != null) {
      comic.setProgressStatus(comicInfo.progressStatus());
    }
    if (isNotBlank(comicInfo.thumbUrl())) {
      comic.setThumbUrl(comicInfo.thumbUrl());
    }
    if (isNotBlank(comicInfo.author())) {
      comic.setAuthor(comicInfo.author());
    }
    if (comicInfo.alternativeNames() != null) {
      comic.setAlternativeNames(comicInfo.alternativeNames());
    }
    if (comicInfo.likes() != null) {
      comic.setLikes(comicInfo.likes());
    }
    if (comicInfo.follows() != null) {
      comic.setFollows(comicInfo.follows());
    }
    if (comicInfo.totalChapters() != null) {
      comic.setTotalChapters(comicInfo.totalChapters());
    }
    if (comicInfo.lastChapterUpdatedAt() != null) {
      comic.setLastChapterUpdatedAt(comicInfo.lastChapterUpdatedAt());
    }
    if (comicInfo.ageRating() != null) {
      comic.setAgeRating(comicInfo.ageRating());
    }
    if (comicInfo.gender() != null) {
      comic.setGender(comicInfo.gender());
    }
    if (isNotBlank(comicInfo.country())) {
      comic.setCountry(comicInfo.country());
    }
  }

  @Transactional
  public void createOrUpdateChapter(Comic comic, ChapterInfo chapterInfo) {
    if (comic == null || chapterInfo == null) {
      throw new IllegalArgumentException("Comic and ChapterInfo are required");
    }

    var existingOpt = isNotBlank(chapterInfo.source())
        ? chapterRepository.findBySource(chapterInfo.source())
        : chapterRepository.findByComicAndChapterName(comic, chapterInfo.chapterName());

    Chapter chapter;
    if (existingOpt.isPresent()) {
      chapter = existingOpt.get();
      log.debug("Updating existing chapter: {} from source: {}", chapter.getId(), chapterInfo.source());
      if (isNotBlank(chapterInfo.chapterTitle())) {
        chapter.setChapterTitle(chapterInfo.chapterTitle());
      }
      if (isNotBlank(chapterInfo.source())) {
        chapter.setSource(chapterInfo.source());
      }
    } else {
      log.debug("Creating new chapter: {} for comic: {}", chapterInfo.chapterName(), comic.getId());
      chapter = Chapter.builder()
          .comic(comic)
          .chapterName(chapterInfo.chapterName())
          .chapterTitle(isNotBlank(chapterInfo.chapterTitle()) ? chapterInfo.chapterTitle() : "")
          .source(chapterInfo.source())
          .isBackedUp(false)
          .build();
    }

    chapter = chapterRepository.save(chapter);

    // Save chapter images
    if (chapterInfo.imageUrls() != null && !chapterInfo.imageUrls().isEmpty()) {
      chapterImageService.saveChapterImages(chapter, chapterInfo.imageUrls());
    }
  }

  @Transactional(readOnly = true)
  public Page<ComicResponse> getHomeComics(Pageable pageable) {
    // Query from database with filter: status = ACTIVE AND merged_comic_id IS NULL
    Specification<Comic> spec = (root, query, cb) -> cb.and(
        cb.equal(root.get("status"), ACTIVE),
        cb.isNull(root.get("mergedComic"))
    );

    var comics = comicRepository.findAll(spec, pageable);
    return comics.map(comic -> {
      var response = comicMapper.toResponse(comic);
      return enrichComicResponse(comic, response);
    });
  }

  @Transactional(readOnly = true)
  public ComicResponse getComicBySlug(String slug) {
    var comic = comicRepository.findBySlug(slug)
        .orElseThrow(() -> new IllegalArgumentException("Comic not found: " + slug));
    var response = comicMapper.toResponse(comic);
    return enrichComicResponse(comic, response);
  }

  @Transactional(readOnly = true)
  public ComicResponse getComicById(Long id) {
    var comic = comicRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Comic not found: " + id));
    var response = comicMapper.toResponse(comic);
    return enrichComicResponse(comic, response);
  }

  @Transactional
  public void incrementViews(Long id) {
    var comic = comicRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Comic not found: " + id));
    comic.setViews(comic.getViews() + 1);
    comicRepository.save(comic);
  }

  @Transactional(readOnly = true)
  public Page<ComicResponse> getComicsByCategory(String categorySlug, Pageable pageable) {
    Specification<Comic> spec = (root, query, cb) -> {
      var categoryJoin = root.join("categories");
      return cb.and(
          cb.equal(categoryJoin.get("slug"), categorySlug),
          cb.equal(root.get("status"), ACTIVE),
          cb.isNull(root.get("mergedComic"))
      );
    };
    var comics = comicRepository.findAll(spec, pageable);
    return comics.map(comic -> enrichComicResponse(comic, comicMapper.toResponse(comic)));
  }

  @Transactional(readOnly = true)
  public Page<ComicResponse> getComicList(String type, Pageable pageable) {
    Specification<Comic> spec = (root, query, cb) -> cb.and(
        cb.equal(root.get("status"), ACTIVE),
        cb.isNull(root.get("mergedComic"))
    );
    var comics = comicRepository.findAll(spec, pageable);
    return comics.map(comic -> enrichComicResponse(comic, comicMapper.toResponse(comic)));
  }

  @Transactional(readOnly = true)
  public Page<ComicResponse> searchComics(String query, Pageable pageable) {
    Specification<Comic> spec = (root, criteriaQuery, cb) -> {
      var searchPattern = "%" + query.toLowerCase() + "%";
      return cb.and(
          cb.or(
              cb.like(cb.lower(root.get("name")), searchPattern),
              cb.like(cb.lower(root.get("originName")), searchPattern),
              cb.like(cb.lower(root.get("author")), searchPattern)
          ),
          cb.equal(root.get("status"), ACTIVE),
          cb.isNull(root.get("mergedComic"))
      );
    };
    var comics = comicRepository.findAll(spec, pageable);
    return comics.map(comic -> enrichComicResponse(comic, comicMapper.toResponse(comic)));
  }

  @Transactional(readOnly = true)
  public Page<ComicResponse> advancedSearch(String keywords, String genres, String notGenres,
                                            String country, String status, Integer minChapter,
                                            String sort, Pageable pageable) {
    Specification<Comic> spec = (root, query, cb) -> {
      var predicates = new ArrayList<Predicate>();

      predicates.add(cb.equal(root.get("status"), ACTIVE));
      predicates.add(cb.isNull(root.get("mergedComic")));

      if (isNotBlank(keywords)) {
        var searchPattern = "%" + keywords.toLowerCase() + "%";
        predicates.add(cb.or(
            cb.like(cb.lower(root.get("name")), searchPattern),
            cb.like(cb.lower(root.get("originName")), searchPattern)
        ));
      }

      if (isNotBlank(country)) {
        predicates.add(cb.equal(root.get("country"), country));
      }

      if (minChapter != null && minChapter > 0) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("totalChapters"), minChapter));
      }

      return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
    };

    var comics = comicRepository.findAll(spec, pageable);
    return comics.map(comic -> enrichComicResponse(comic, comicMapper.toResponse(comic)));
  }

  @Transactional(readOnly = true)
  public List<Category> getAllCategories() {
    return categoryRepository.findAllByOrderByNameAsc();
  }

  private ComicResponse enrichComicResponse(Comic comic, ComicResponse response) {
    // Enrich with followCount and chapterCount
    var followCount = comic.getUserFollows() != null ? (long) comic.getUserFollows().size() : 0L;
    var chapterCount = chapterRepository.countByComic(comic);

    return new ComicResponse(
        response.id(),
        response.name(),
        response.slug(),
        response.originName(),
        response.content(),
        response.status(),
        response.progressStatus(),
        response.thumbUrl(),
        response.views(),
        response.author(),
        response.isHot(),
        comic.getLikes(), // Use actual likes from entity
        comic.getFollows(), // Use actual follows from entity
        followCount,
        chapterCount,
        comic.getTotalChapters(),
        comic.getLastChapterUpdatedAt(),
        comic.getSource(), // Use actual source from entity
        comic.getAlternativeNames(), // Use actual alternative names from entity
        comic.getAgeRating(), // Use actual age rating from entity
        comic.getGender(), // Use actual gender from entity
        comic.getCountry(), // Use actual country from entity
        response.createdAt(),
        response.updatedAt()
    );
  }

}
