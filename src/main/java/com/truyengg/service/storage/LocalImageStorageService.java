package com.truyengg.service.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import static java.lang.String.format;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.deleteIfExists;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.newDirectoryStream;
import static java.nio.file.Files.readAllBytes;
import static java.nio.file.Files.write;
import static java.nio.file.Path.of;
import static org.apache.commons.io.IOUtils.EMPTY_BYTE_ARRAY;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Local filesystem implementation of ImageStorageService.
 * Stores images in a local directory structure: {basePath}/comics/{comicId}/{chapterId}/{imageName}
 */
@Service
@ConditionalOnProperty(name = "truyengg.storage.type", havingValue = "local")
@Slf4j
public class LocalImageStorageService implements ImageStorageService {

  @Value("${truyengg.storage.local.base-path:./uploads}")
  private String basePath;

  @PostConstruct
  public void init() {
    try {
      var uploadDir = of(basePath);
      if (!exists(uploadDir)) {
        createDirectories(uploadDir);
      }
    } catch (IOException e) {
      log.error("Failed to initialize local storage directory: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to initialize local storage", e);
    }
  }

  @Override
  public String uploadImage(String comicId, String chapterId, String imageName,
                            byte[] imageData, String contentType) {
    try {
      var filePath = buildFilePath(comicId, chapterId, imageName);
      createDirectories(filePath.getParent());
      write(filePath, imageData);
      return getImagePath(comicId, chapterId, imageName);
    } catch (IOException e) {
      log.error("Error uploading image to local storage: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to upload image to local storage", e);
    }
  }

  @Override
  public String uploadImage(String comicId, String chapterId, String imageName,
                            InputStream inputStream, String contentType, long size) {
    try {
      var imageData = inputStream.readAllBytes();
      return uploadImage(comicId, chapterId, imageName, imageData, contentType);
    } catch (IOException e) {
      log.error("Error reading input stream for upload: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to read image data", e);
    }
  }

  @Override
  public byte[] getImage(String comicId, String chapterId, String imageName) {
    try {
      var filePath = buildFilePath(comicId, chapterId, imageName);
      if (!exists(filePath)) {
        return EMPTY_BYTE_ARRAY;
      }
      return readAllBytes(filePath);
    } catch (IOException e) {
      log.error("Error reading image from local storage: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to read image from local storage", e);
    }
  }

  @Override
  public void deleteImage(String comicId, String chapterId, String imageName) {
    try {
      var filePath = buildFilePath(comicId, chapterId, imageName);
      deleteIfExists(filePath);
    } catch (IOException e) {
      log.error("Error deleting image from local storage: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to delete image from local storage", e);
    }
  }

  @Override
  public void deleteChapterImages(String comicId, String chapterId) {
    try {
      var chapterDir = of(basePath, "comics", comicId, chapterId);
      if (!exists(chapterDir)) {
        return;
      }

      deleteDirectoryContents(chapterDir);
      deleteIfExists(chapterDir);
    } catch (IOException e) {
      log.error("Error deleting chapter images from local storage: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to delete chapter images from local storage", e);
    }
  }

  @Override
  public void deleteComicImages(String comicId) {
    try {
      var comicDir = Path.of(basePath, "comics", comicId);
      if (!exists(comicDir)) {
        return;
      }

      deleteDirectoryRecursively(comicDir);
    } catch (IOException e) {
      log.error("Error deleting comic images from local storage for comic {}: {}", comicId, e.getMessage(), e);
      // Don't throw exception - allow retry to continue even if deletion fails
    }
  }

  @Override
  public String getImagePath(String comicId, String chapterId, String imageName) {
    if (isBlank(imageName)) {
      return format("comics/%s/%s/", comicId, chapterId);
    }
    return format("comics/%s/%s/%s", comicId, chapterId, imageName);
  }

  @Override
  public boolean imageExists(String comicId, String chapterId, String imageName) {
    var filePath = buildFilePath(comicId, chapterId, imageName);
    return exists(filePath);
  }

  private Path buildFilePath(String comicId, String chapterId, String imageName) {
    return of(basePath, "comics", comicId, chapterId, imageName);
  }

  private int deleteDirectoryContents(Path directory) throws IOException {
    var deletedCount = 0;
    try (var stream = newDirectoryStream(directory)) {
      for (var path : stream) {
        if (isDirectory(path)) {
          deletedCount += deleteDirectoryRecursively(path);
        } else {
          deleteIfExists(path);
          deletedCount++;
        }
      }
    }
    return deletedCount;
  }

  private int deleteDirectoryRecursively(Path directory) throws IOException {
    var deletedCount = deleteDirectoryContents(directory);
    deleteIfExists(directory);
    return deletedCount;
  }
}

