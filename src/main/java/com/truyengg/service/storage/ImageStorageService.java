package com.truyengg.service.storage;

import java.io.InputStream;

/**
 * Abstraction for image storage operations.
 * Implementations can use MinIO, local filesystem, or other storage backends.
 */
public interface ImageStorageService {

  /**
   * Upload an image to storage.
   *
   * @param comicId     the comic ID
   * @param chapterId   the chapter ID
   * @param imageName   the image file name
   * @param imageData   the image bytes
   * @param contentType the MIME type (e.g., "image/webp")
   * @return the relative path to the stored image
   */
  String uploadImage(String comicId, String chapterId, String imageName,
                     byte[] imageData, String contentType);

  /**
   * Upload an image to storage using InputStream.
   *
   * @param comicId     the comic ID
   * @param chapterId   the chapter ID
   * @param imageName   the image file name
   * @param inputStream the input stream containing image data
   * @param contentType the MIME type
   * @param size        the size in bytes
   * @return the relative path to the stored image
   */
  String uploadImage(String comicId, String chapterId, String imageName,
                     InputStream inputStream, String contentType, long size);

  /**
   * Get an image from storage.
   *
   * @param comicId   the comic ID
   * @param chapterId the chapter ID
   * @param imageName the image file name
   * @return the image bytes, or null if not found
   */
  byte[] getImage(String comicId, String chapterId, String imageName);

  /**
   * Delete a single image from storage.
   *
   * @param comicId   the comic ID
   * @param chapterId the chapter ID
   * @param imageName the image file name
   */
  void deleteImage(String comicId, String chapterId, String imageName);

  /**
   * Delete all images for a chapter.
   *
   * @param comicId   the comic ID
   * @param chapterId the chapter ID
   */
  void deleteChapterImages(String comicId, String chapterId);

  /**
   * Delete all images for a comic (all chapters).
   *
   * @param comicId the comic ID
   */
  void deleteComicImages(String comicId);

  /**
   * Get the relative path for an image.
   * Format: comics/{comicId}/{chapterId}/{imageName}
   *
   * @param comicId   the comic ID
   * @param chapterId the chapter ID
   * @param imageName the image file name
   * @return the relative path
   */
  String getImagePath(String comicId, String chapterId, String imageName);

  /**
   * Check if an image exists in storage.
   *
   * @param comicId   the comic ID
   * @param chapterId the chapter ID
   * @param imageName the image file name
   * @return true if the image exists
   */
  boolean imageExists(String comicId, String chapterId, String imageName);
}

