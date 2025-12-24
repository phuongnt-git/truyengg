package com.truyengg.service;

import com.truyengg.domain.entity.Chapter;
import com.truyengg.domain.entity.ChapterImage;
import com.truyengg.domain.repository.ChapterImageRepository;
import com.truyengg.model.dto.ChapterImageInfo;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static lombok.AccessLevel.PRIVATE;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
@Slf4j
public class ChapterImageService {

  ChapterImageRepository chapterImageRepository;

  @Transactional
  public void saveChapterImages(Chapter chapter, List<ChapterImageInfo> imageInfos) {
    if (chapter == null || imageInfos == null || imageInfos.isEmpty()) {
      return;
    }

    var existingImages = chapterImageRepository.findByChapterIdOrderByImageOrderAsc(chapter.getId());
    if (!existingImages.isEmpty()) {
      chapterImageRepository.deleteAll(existingImages);
    }

    var images = new ArrayList<ChapterImage>();
    for (int i = 0; i < imageInfos.size(); i++) {
      var imageInfo = imageInfos.get(i);
      var image = ChapterImage.builder()
          .chapter(chapter)
          .path(imageInfo.path() != null ? imageInfo.path() : imageInfo.originalUrl())
          .originalUrl(imageInfo.originalUrl())
          .imageOrder(imageInfo.imageOrder() != null ? imageInfo.imageOrder() : i + 1)
          .manualOrder(imageInfo.manualOrder())
          .isDownloaded(imageInfo.isDownloaded() != null && imageInfo.isDownloaded())
          .isVisible(imageInfo.isVisible() != null && imageInfo.isVisible())
          .build();
      images.add(image);
    }

    chapterImageRepository.saveAll(images);
  }

  @Transactional
  public void updateImageDownloadStatus(Long imageId, String storedUrl, boolean isDownloaded) {
    var imageOpt = chapterImageRepository.findById(imageId);
    if (imageOpt.isPresent()) {
      var image = imageOpt.get();
      if (storedUrl != null) {
        image.setPath(storedUrl);
      }
      image.setIsDownloaded(isDownloaded);
      chapterImageRepository.save(image);
    }
  }

  @Transactional
  public void softDeleteImage(Long imageId) {
    var imageOpt = chapterImageRepository.findById(imageId);
    if (imageOpt.isPresent()) {
      var image = imageOpt.get();
      image.setDeletedAt(ZonedDateTime.now());
      chapterImageRepository.save(image);
    }
  }

  @Transactional
  public void hardDeleteImage(Long imageId) {
    chapterImageRepository.deleteById(imageId);
  }

  @Transactional
  public void restoreImage(Long imageId) {
    var imageOpt = chapterImageRepository.findById(imageId);
    if (imageOpt.isPresent()) {
      var image = imageOpt.get();
      image.setDeletedAt(null);
      chapterImageRepository.save(image);
    }
  }

  @Transactional
  public void bulkSoftDeleteImages(List<Long> imageIds) {
    var images = chapterImageRepository.findAllById(imageIds);
    var now = ZonedDateTime.now();
    images.forEach(image -> image.setDeletedAt(now));
    chapterImageRepository.saveAll(images);
  }

  @Transactional
  public void bulkHardDeleteImages(List<Long> imageIds) {
    chapterImageRepository.deleteAllById(imageIds);
  }

  @Transactional
  public void bulkRestoreImages(List<Long> imageIds) {
    var images = chapterImageRepository.findAllById(imageIds);
    images.forEach(image -> image.setDeletedAt(null));
    chapterImageRepository.saveAll(images);
  }

  @Transactional
  public void deleteAllImagesInChapter(Long chapterId) {
    var images = chapterImageRepository.findByChapterIdOrderByImageOrderAsc(chapterId);
    chapterImageRepository.deleteAll(images);
  }

  @Transactional
  public void updateImageVisibility(Long imageId, boolean isVisible) {
    var imageOpt = chapterImageRepository.findById(imageId);
    if (imageOpt.isPresent()) {
      var image = imageOpt.get();
      image.setIsVisible(isVisible);
      chapterImageRepository.save(image);
    }
  }

  @Transactional
  public void updateImageOrder(Long imageId, Integer manualOrder) {
    var imageOpt = chapterImageRepository.findById(imageId);
    if (imageOpt.isPresent()) {
      var image = imageOpt.get();
      image.setManualOrder(manualOrder);
      chapterImageRepository.save(image);
    }
  }
}

