package com.truyengg.service;

import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioService {

  private final MinioClient minioClient;

  @Value("${truyengg.minio.bucket-name:truyengg-images}")
  private String bucketName;

  @Value("${truyengg.minio.endpoint:http://localhost:9000}")
  private String endpoint;

  @PostConstruct
  public void init() {
    try {
      ensureBucketExists();
      log.info("MinIO bucket '{}' is ready", bucketName);
    } catch (Exception e) {
      log.error("Failed to initialize MinIO bucket: {}", e.getMessage(), e);
      // Don't throw exception to allow app to start even if MinIO is not available
      // The bucket will be created on first upload
    }
  }

  public void ensureBucketExists() {
    try {
      var exists = minioClient.bucketExists(BucketExistsArgs.builder()
          .bucket(bucketName)
          .build());

      if (!exists) {
        minioClient.makeBucket(MakeBucketArgs.builder()
            .bucket(bucketName)
            .build());
        log.info("Created MinIO bucket: {}", bucketName);
      }
    } catch (Exception e) {
      log.error("Error ensuring bucket exists: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to ensure bucket exists", e);
    }
  }

  public String uploadImage(String comicId, String chapterId, String imageName,
                            byte[] imageData, String contentType) {
    return uploadImage(comicId, chapterId, imageName,
        new ByteArrayInputStream(imageData), contentType, imageData.length);
  }

  public String uploadImage(String comicId, String chapterId, String imageName,
                            InputStream inputStream, String contentType, long size) {
    try {
      ensureBucketExists();

      var objectName = buildObjectName(comicId, chapterId, imageName);

      minioClient.putObject(PutObjectArgs.builder()
          .bucket(bucketName)
          .object(objectName)
          .stream(inputStream, size, -1)
          .contentType(contentType)
          .build());

      var path = getImagePath(comicId, chapterId, imageName);
      log.debug("Uploaded image to MinIO: {}", objectName);
      return path;
    } catch (Exception e) {
      log.error("Error uploading image to MinIO: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to upload image to MinIO", e);
    }
  }

  public void deleteImage(String comicId, String chapterId, String imageName) {
    try {
      var objectName = buildObjectName(comicId, chapterId, imageName);
      minioClient.removeObject(RemoveObjectArgs.builder()
          .bucket(bucketName)
          .object(objectName)
          .build());
      log.debug("Deleted image from MinIO: {}", objectName);
    } catch (Exception e) {
      log.error("Error deleting image from MinIO: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to delete image from MinIO", e);
    }
  }

  public void deleteChapterImages(String comicId, String chapterId) {
    try {
      var prefix = buildObjectName(comicId, chapterId, "");
      var objectsList = minioClient.listObjects(ListObjectsArgs.builder()
          .bucket(bucketName)
          .prefix(prefix)
          .recursive(true)
          .build());

      int deletedCount = 0;
      for (Result<Item> result : objectsList) {
        var item = result.get();
        minioClient.removeObject(RemoveObjectArgs.builder()
            .bucket(bucketName)
            .object(item.objectName())
            .build());
        deletedCount++;
      }
      log.debug("Deleted {} images from MinIO for comic {} chapter {}", deletedCount, comicId, chapterId);
    } catch (Exception e) {
      log.error("Error deleting chapter images from MinIO: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to delete chapter images from MinIO", e);
    }
  }

  /**
   * Delete all images for a comic (all chapters)
   */
  public void deleteComicImages(String comicId) {
    try {
      var prefix = "comics/" + comicId + "/";
      var objectsList = minioClient.listObjects(ListObjectsArgs.builder()
          .bucket(bucketName)
          .prefix(prefix)
          .recursive(true)
          .build());

      int deletedCount = 0;
      for (Result<Item> result : objectsList) {
        var item = result.get();
        minioClient.removeObject(RemoveObjectArgs.builder()
            .bucket(bucketName)
            .object(item.objectName())
            .build());
        deletedCount++;
      }
      log.info("Deleted {} images from MinIO for comic {}", deletedCount, comicId);
    } catch (Exception e) {
      log.error("Error deleting comic images from MinIO for comic {}: {}", comicId, e.getMessage(), e);
      // Don't throw exception - allow retry to continue even if MinIO deletion fails
      // Files will be overwritten anyway during retry
    }
  }

  public String getPresignedUrl(String comicId, String chapterId, String imageName, int expiryInSeconds) {
    try {
      var objectName = buildObjectName(comicId, chapterId, imageName);
      return minioClient.getPresignedObjectUrl(
          io.minio.GetPresignedObjectUrlArgs.builder()
              .method(io.minio.http.Method.GET)
              .bucket(bucketName)
              .object(objectName)
              .expiry(expiryInSeconds)
              .build());
    } catch (Exception e) {
      log.error("Error generating presigned URL: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to generate presigned URL", e);
    }
  }

  /**
   * Get relative path for image (without endpoint)
   * Format: comics/{comicId}/{chapterId}/{imageName}
   */
  public String getImagePath(String comicId, String chapterId, String imageName) {
    return buildObjectName(comicId, chapterId, imageName);
  }

  /**
   * Get full URL for image (deprecated - use getImagePath() and proxy endpoint instead)
   *
   * @deprecated Use getImagePath() and proxy through ImageProxyController
   */
  @Deprecated
  public String getImageUrl(String comicId, String chapterId, String imageName) {
    var objectName = buildObjectName(comicId, chapterId, imageName);
    return String.format("%s/%s/%s", endpoint, bucketName, objectName);
  }

  private String buildObjectName(String comicId, String chapterId, String imageName) {
    if (imageName == null || imageName.isEmpty()) {
      return String.format("comics/%s/%s/", comicId, chapterId);
    }
    return String.format("comics/%s/%s/%s", comicId, chapterId, imageName);
  }
}
