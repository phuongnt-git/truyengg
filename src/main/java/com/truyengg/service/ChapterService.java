package com.truyengg.service;

import com.truyengg.domain.entity.Chapter;
import com.truyengg.domain.entity.Comic;
import com.truyengg.domain.repository.ChapterRepository;
import com.truyengg.domain.repository.ComicRepository;
import com.truyengg.exception.ResourceNotFoundException;
import com.truyengg.model.response.ChapterResponse;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

import static lombok.AccessLevel.PRIVATE;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
@Slf4j
public class ChapterService {

  ChapterRepository chapterRepository;
  ComicRepository comicRepository;
  ChapterImageService chapterImageService;

  @Transactional(readOnly = true)
  @Cacheable(value = "chaptersByComic", key = "#comicSlug")
  public List<ChapterResponse> getChaptersByComicSlug(String comicSlug) {
    Comic comic = comicRepository.findBySlug(comicSlug)
        .orElseThrow(() -> new ResourceNotFoundException("Comic not found"));

    return chapterRepository.findByComicOrderByCreatedAtAsc(comic).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "chapterByComicAndName", key = "#comicSlug + '-' + #chapterName")
  public ChapterResponse getChapterByComicAndChapterName(String comicSlug, String chapterName) {
    Comic comic = comicRepository.findBySlug(comicSlug)
        .orElseThrow(() -> new ResourceNotFoundException("Comic not found"));

    Chapter chapter = chapterRepository.findByComicAndChapterName(comic, chapterName)
        .orElseThrow(() -> new ResourceNotFoundException("Chapter not found"));

    return toResponse(chapter);
  }

  @Transactional
  @CacheEvict(value = {"chaptersByComic", "chapterByComicAndName"}, allEntries = true)
  public ChapterResponse syncChapterFromApi(Long comicId, String chapterName, String chapterApiUrl) {
    var comic = comicRepository.findById(comicId)
        .orElseThrow(() -> new ResourceNotFoundException("Comic not found"));

    var chapter = chapterRepository.findByComicAndChapterName(comic, chapterName)
        .orElse(Chapter.builder()
            .comic(comic)
            .chapterName(chapterName)
            .source(chapterApiUrl)
            .build());

    chapter.setSource(chapterApiUrl);
    chapter = chapterRepository.save(chapter);

    return toResponse(chapter);
  }

  @Transactional
  public void softDeleteChapter(Long chapterId) {
    var chapterOpt = chapterRepository.findById(chapterId);
    if (chapterOpt.isPresent()) {
      var chapter = chapterOpt.get();
      chapter.setDeletedAt(ZonedDateTime.now());
      chapterRepository.save(chapter);
      log.debug("Soft deleted chapter {}", chapterId);
    }
  }

  @Transactional
  public void hardDeleteChapter(Long chapterId) {
    var chapterOpt = chapterRepository.findById(chapterId);
    if (chapterOpt.isPresent()) {
      var chapter = chapterOpt.get();
      // Delete all images first
      chapterImageService.deleteAllImagesInChapter(chapterId);
      // Then delete the chapter
      chapterRepository.delete(chapter);
      log.debug("Hard deleted chapter {} and all its images", chapterId);
    }
  }

  @Transactional
  public void restoreChapter(Long chapterId) {
    var chapterOpt = chapterRepository.findById(chapterId);
    if (chapterOpt.isPresent()) {
      var chapter = chapterOpt.get();
      chapter.setDeletedAt(null);
      chapterRepository.save(chapter);
      log.debug("Restored chapter {}", chapterId);
    }
  }

  @Transactional
  public void bulkSoftDeleteChapters(List<Long> chapterIds) {
    var chapters = chapterRepository.findAllById(chapterIds);
    var now = ZonedDateTime.now();
    chapters.forEach(chapter -> chapter.setDeletedAt(now));
    chapterRepository.saveAll(chapters);
    log.debug("Soft deleted {} chapters", chapters.size());
  }

  @Transactional
  public void bulkHardDeleteChapters(List<Long> chapterIds) {
    for (var chapterId : chapterIds) {
      hardDeleteChapter(chapterId);
    }
    log.debug("Hard deleted {} chapters", chapterIds.size());
  }

  @Transactional
  public void bulkRestoreChapters(List<Long> chapterIds) {
    var chapters = chapterRepository.findAllById(chapterIds);
    chapters.forEach(chapter -> chapter.setDeletedAt(null));
    chapterRepository.saveAll(chapters);
    log.debug("Restored {} chapters", chapters.size());
  }

  @Transactional
  public void deleteAllChaptersInComic(Long comicId) {
    var comicOpt = comicRepository.findById(comicId);
    if (comicOpt.isPresent()) {
      var comic = comicOpt.get();
      var chapters = chapterRepository.findByComic(comic);
      for (var chapter : chapters) {
        hardDeleteChapter(chapter.getId());
      }
      log.debug("Deleted all {} chapters in comic {}", chapters.size(), comicId);
    }
  }

  private ChapterResponse toResponse(Chapter chapter) {
    return new ChapterResponse(
        chapter.getId(),
        chapter.getComic().getId(),
        chapter.getComic().getSlug(),
        chapter.getChapterName(),
        chapter.getChapterTitle(),
        chapter.getSource(),
        chapter.getCreatedAt()
    );
  }
}
