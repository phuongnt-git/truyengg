package com.truyengg.service;

import com.truyengg.domain.entity.Chapter;
import com.truyengg.domain.entity.Comic;
import com.truyengg.domain.enums.ComicProgressStatus;
import com.truyengg.domain.enums.ComicStatus;
import com.truyengg.domain.repository.ChapterRepository;
import com.truyengg.domain.repository.ComicRepository;
import com.truyengg.model.dto.ChapterInfo;
import com.truyengg.model.dto.ComicInfo;
import com.truyengg.model.mapper.ComicMapper;
import com.truyengg.model.response.ComicResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComicService {

  private final ComicRepository comicRepository;
  private final ChapterRepository chapterRepository;
  private final ComicMapper comicMapper;
  private final OTruyenApiService otruyenApiService;
  private final SlugService slugService;
  private final ComicDuplicateService comicDuplicateService;
  private final ChapterImageService chapterImageService;

  @Transactional
  public Comic createOrUpdateComic(ComicInfo comicInfo) {
    if (comicInfo == null || StringUtils.isBlank(comicInfo.source())) {
      throw new IllegalArgumentException("ComicInfo and source URL are required");
    }

    var existingOpt = comicRepository.findBySource(comicInfo.source());

    Comic comic;
    if (existingOpt.isPresent()) {
      comic = existingOpt.get();
      log.debug("Updating existing comic: {} from source: {}", comic.getId(), comicInfo.source());
      updateComicFields(comic, comicInfo);
    } else {
      log.debug("Creating new comic from source: {}", comicInfo.source());
      var slug = StringUtils.isNotBlank(comicInfo.slug())
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
        var bestMatch = duplicates.get(0);
        if (bestMatch.similarity() >= 0.9) {
          // Auto-merge
          log.info("Auto-merging comic {} into {} (similarity: {})",
              comic.getId(), bestMatch.comic().getId(), bestMatch.similarity());
          comic = comicDuplicateService.mergeComics(bestMatch.comic(), comic, null);
        } else if (bestMatch.similarity() >= 0.7) {
          // Mark for review
          comic.setStatus(ComicStatus.DUPLICATE_DETECTED);
          comic = comicRepository.save(comic);
          log.info("Marked comic {} as DUPLICATE_DETECTED (similarity: {})",
              comic.getId(), bestMatch.similarity());
        } else {
          // Set as ACTIVE
          comic.setStatus(ComicStatus.ACTIVE);
          comic = comicRepository.save(comic);
        }
      } else {
        // No duplicates found, set as ACTIVE
        comic.setStatus(ComicStatus.ACTIVE);
        comic = comicRepository.save(comic);
      }
    }

    return comic;
  }

  private void updateComicFields(Comic comic, ComicInfo comicInfo) {
    if (StringUtils.isNotBlank(comicInfo.name())) {
      comic.setName(comicInfo.name());
    }
    if (StringUtils.isNotBlank(comicInfo.originName())) {
      comic.setOriginName(comicInfo.originName());
    }
    if (StringUtils.isNotBlank(comicInfo.content())) {
      comic.setContent(comicInfo.content());
    }
    if (comicInfo.progressStatus() != null) {
      comic.setProgressStatus(comicInfo.progressStatus());
    }
    if (StringUtils.isNotBlank(comicInfo.thumbUrl())) {
      comic.setThumbUrl(comicInfo.thumbUrl());
    }
    if (StringUtils.isNotBlank(comicInfo.author())) {
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
    if (StringUtils.isNotBlank(comicInfo.country())) {
      comic.setCountry(comicInfo.country());
    }
  }

  @Transactional
  public void createOrUpdateChapter(Comic comic, ChapterInfo chapterInfo) {
    if (comic == null || chapterInfo == null) {
      throw new IllegalArgumentException("Comic and ChapterInfo are required");
    }

    var existingOpt = StringUtils.isNotBlank(chapterInfo.source())
        ? chapterRepository.findBySource(chapterInfo.source())
        : chapterRepository.findByComicAndChapterName(comic, chapterInfo.chapterName());

    Chapter chapter;
    if (existingOpt.isPresent()) {
      chapter = existingOpt.get();
      log.debug("Updating existing chapter: {} from source: {}", chapter.getId(), chapterInfo.source());
      if (StringUtils.isNotBlank(chapterInfo.chapterTitle())) {
        chapter.setChapterTitle(chapterInfo.chapterTitle());
      }
      if (StringUtils.isNotBlank(chapterInfo.source())) {
        chapter.setSource(chapterInfo.source());
      }
    } else {
      log.debug("Creating new chapter: {} for comic: {}", chapterInfo.chapterName(), comic.getId());
      chapter = Chapter.builder()
          .comic(comic)
          .chapterName(chapterInfo.chapterName())
          .chapterTitle(StringUtils.isNotBlank(chapterInfo.chapterTitle()) ? chapterInfo.chapterTitle() : "")
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
        cb.equal(root.get("status"), ComicStatus.ACTIVE),
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
    var comicOpt = comicRepository.findBySlug(slug);
    if (comicOpt.isPresent()) {
      var comic = comicOpt.get();
      var response = comicMapper.toResponse(comic);
      return enrichComicResponse(comic, response);
    }

    var apiResponse = otruyenApiService.getComicDetails(slug);
    return convertApiResponseToComicResponse(apiResponse);
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
    var apiResponse = otruyenApiService.getComicsByCategory(categorySlug,
        pageable.getPageNumber() + 1, pageable.getPageSize());
    return convertApiResponseToPage(apiResponse, pageable);
  }

  @Transactional(readOnly = true)
  public Page<ComicResponse> getComicList(String type, Pageable pageable) {
    var apiResponse = otruyenApiService.getComicList(type,
        pageable.getPageNumber() + 1, pageable.getPageSize());
    return convertApiResponseToPage(apiResponse, pageable);
  }

  @Transactional(readOnly = true)
  public Page<ComicResponse> searchComics(String query, Pageable pageable) {
    var apiResponse = otruyenApiService.searchComics(query);
    return convertApiResponseToPage(apiResponse, pageable);
  }

  @Transactional(readOnly = true)
  public Page<ComicResponse> advancedSearch(String keywords, String genres, String notGenres,
                                            String country, String status, Integer minChapter,
                                            String sort, Pageable pageable) {
    var params = new java.util.HashMap<String, Object>();
    if (keywords != null) params.put("keywords", keywords);
    if (genres != null) params.put("genres", genres);
    if (notGenres != null) params.put("notGenres", notGenres);
    if (country != null) params.put("country", country);
    if (status != null) params.put("status", status);
    if (minChapter != null) params.put("minChapter", minChapter);
    if (sort != null) params.put("sort", sort);

    var apiResponse = otruyenApiService.advancedSearch(params);
    return convertApiResponseToPage(apiResponse, pageable);
  }

  @Transactional
  public ComicResponse syncComicFromApi(String slug) {
    var comicOpt = comicRepository.findBySlug(slug);
    if (comicOpt.isEmpty()) {
      throw new IllegalArgumentException("Comic not found: " + slug);
    }

    var comic = comicOpt.get();
    var apiResponse = otruyenApiService.getComicDetails(comic.getSlug());

    updateComicFromApiResponse(comic, apiResponse);
    comic = comicRepository.save(comic);

    var response = comicMapper.toResponse(comic);
    return enrichComicResponse(comic, response);
  }

  private Page<ComicResponse> convertApiResponseToPage(Map<String, Object> apiResponse, Pageable pageable) {
    if (apiResponse == null || !apiResponse.containsKey("data")) {
      return new PageImpl<>(List.of(), pageable, 0);
    }

    @SuppressWarnings("unchecked")
    var data = (Map<String, Object>) apiResponse.get("data");
    if (data == null || !data.containsKey("items")) {
      return new PageImpl<>(List.of(), pageable, 0);
    }

    @SuppressWarnings("unchecked")
    var items = (List<Map<String, Object>>) data.get("items");
    if (items == null) {
      return new PageImpl<>(List.of(), pageable, 0);
    }

    var responses = items.stream()
        .map(this::convertApiItemToComicResponse)
        .toList();

    @SuppressWarnings("unchecked") var total = data.containsKey("pagination")
        ? ((Map<String, Object>) data.get("pagination")).getOrDefault("total", items.size())
        : items.size();

    return new PageImpl<>(responses, pageable, ((Number) total).longValue());
  }

  private ComicResponse convertApiResponseToComicResponse(Map<String, Object> apiResponse) {
    if (apiResponse == null || !apiResponse.containsKey("data")) {
      throw new IllegalArgumentException("Invalid API response");
    }

    @SuppressWarnings("unchecked")
    var data = (Map<String, Object>) apiResponse.get("data");
    return convertApiItemToComicResponse(data);
  }

  private ComicResponse convertApiItemToComicResponse(Map<String, Object> item) {
    // Convert API response item to ComicResponse
    // This is a simplified conversion - may need adjustment based on actual API structure
    var progressStatus = parseProgressStatus(String.valueOf(item.getOrDefault("status", "ongoing")));
    return new ComicResponse(
        null, // id
        String.valueOf(item.getOrDefault("name", "")),
        String.valueOf(item.getOrDefault("slug", "")),
        String.valueOf(item.getOrDefault("origin_name", "")),
        String.valueOf(item.getOrDefault("content", "")),
        ComicStatus.ACTIVE, // Default status for API responses
        progressStatus,
        otruyenApiService.getImageUrl(String.valueOf(item.getOrDefault("thumb_url", ""))),
        ((Number) item.getOrDefault("views", 0L)).longValue(),
        String.valueOf(item.getOrDefault("author", "")),
        Boolean.parseBoolean(String.valueOf(item.getOrDefault("is_hot", false))),
        ((Number) item.getOrDefault("likes", 0L)).longValue(),
        ((Number) item.getOrDefault("follows", 0L)).longValue(),
        ((Number) item.getOrDefault("follow_count", 0L)).longValue(),
        ((Number) item.getOrDefault("chapter_count", 0L)).longValue(),
        item.containsKey("total_chapters") ? ((Number) item.get("total_chapters")).intValue() : null,
        null, // lastChapterUpdatedAt
        null, // source
        null, // alternativeNames
        null, // ageRating
        null, // gender
        null, // country
        null, // createdAt
        null  // updatedAt
    );
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

  private void updateComicFromApiResponse(Comic comic, Map<String, Object> apiResponse) {
    if (apiResponse == null || !apiResponse.containsKey("data")) {
      return;
    }

    @SuppressWarnings("unchecked")
    var data = (Map<String, Object>) apiResponse.get("data");

    if (data.containsKey("name")) {
      comic.setName(String.valueOf(data.get("name")));
    }
    if (data.containsKey("origin_name")) {
      comic.setOriginName(String.valueOf(data.get("origin_name")));
    }
    if (data.containsKey("content")) {
      comic.setContent(String.valueOf(data.get("content")));
    }
    if (data.containsKey("status")) {
      comic.setProgressStatus(parseProgressStatus(String.valueOf(data.get("status"))));
    }
    if (data.containsKey("thumb_url")) {
      comic.setThumbUrl(otruyenApiService.getImageUrl(String.valueOf(data.get("thumb_url"))));
    }
    if (data.containsKey("author")) {
      comic.setAuthor(String.valueOf(data.get("author")));
    }
  }

  private ComicProgressStatus parseProgressStatus(String status) {
    if (status == null) {
      return ComicProgressStatus.ONGOING;
    }
    return switch (status.toLowerCase()) {
      case "completed", "hoàn thành" -> ComicProgressStatus.COMPLETED;
      case "coming_soon", "sắp ra mắt" -> ComicProgressStatus.COMING_SOON;
      case "onhold", "tạm dừng" -> ComicProgressStatus.ONHOLD;
      case "dropped", "bỏ dở" -> ComicProgressStatus.DROPPED;
      default -> ComicProgressStatus.ONGOING;
    };
  }
}
