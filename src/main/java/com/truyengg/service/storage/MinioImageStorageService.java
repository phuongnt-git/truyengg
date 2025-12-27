package com.truyengg.service.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.apache.commons.io.IOUtils.EMPTY_BYTE_ARRAY;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;

/**
 * MinIO implementation of ImageStorageService.
 * Stores images in MinIO object storage.
 */
@Service
@ConditionalOnProperty(name = "truyengg.storage.type", havingValue = "minio", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class MinioImageStorageService implements ImageStorageService {

  private final MinioClient minioClient;

  @Value("${truyengg.storage.minio.bucket-name:truyengg}")
  private String bucketName;

  @PostConstruct
  public void init() {
    try {
      ensureBucketExists();
      log.info("MinIO bucket '{}' is ready", bucketName);
    } catch (Exception e) {
      log.warn("Failed to initialize MinIO bucket: {}", getRootCauseMessage(e));
      // Don't throw exception to allow app to start even if MinIO is not available
      // The bucket will be created on first upload
    }
  }

  private void ensureBucketExists() {
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
      log.warn("Error ensuring bucket exists: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to ensure bucket exists", e);
    }
  }

  @Override
  public String uploadImage(String comicId, String chapterId, String imageName,
                            byte[] imageData, String contentType) {
    return uploadImage(comicId, chapterId, imageName,
        new ByteArrayInputStream(imageData), contentType, imageData.length);
  }

  @Override
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

      return getImagePath(comicId, chapterId, imageName);
    } catch (Exception e) {
      log.warn("Error uploading image to MinIO: {}", getRootCauseMessage(e));
      throw new IllegalStateException("Failed to upload image to MinIO", e);
    }
  }

  @Override
  public byte[] getImage(String comicId, String chapterId, String imageName) {
    try {
      var objectName = buildObjectName(comicId, chapterId, imageName);
      try (var stream = minioClient.getObject(GetObjectArgs.builder()
          .bucket(bucketName)
          .object(objectName)
          .build())) {
        return stream.readAllBytes();
      }
    } catch (Exception e) {
      log.warn("Error getting image from MinIO: {}", getRootCauseMessage(e));
      return EMPTY_BYTE_ARRAY;
    }
  }

  @Override
  public void deleteImage(String comicId, String chapterId, String imageName) {
    try {
      var objectName = buildObjectName(comicId, chapterId, imageName);
      minioClient.removeObject(RemoveObjectArgs.builder()
          .bucket(bucketName)
          .object(objectName)
          .build());
    } catch (Exception e) {
      log.warn("Error deleting image from MinIO: {}", getRootCauseMessage(e));
      throw new IllegalStateException("Failed to delete image from MinIO", e);
    }
  }

  @Override
  public void deleteChapterImages(String comicId, String chapterId) {
    try {
      var prefix = buildObjectName(comicId, chapterId, EMPTY);
      var deletedCount = delete(prefix);
      log.info("Deleted {} images from MinIO for comic {} chapter {}", deletedCount, comicId, chapterId);
    } catch (Exception e) {
      log.warn("Error deleting chapter images from MinIO: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to delete chapter images from MinIO", e);
    }
  }

  @Override
  public void deleteComicImages(String comicId) {
    try {
      var prefix = "comics/" + comicId + "/";
      var deletedCount = delete(prefix);
      log.info("Deleted {} images from MinIO for comic {}", deletedCount, comicId);
    } catch (Exception e) {
      log.warn("Error deleting comic images from MinIO for comic {}: {}", comicId, e.getMessage(), e);
      // Don't throw exception - allow retry to continue even if MinIO deletion fails
    }
  }

  @Override
  public String getImagePath(String comicId, String chapterId, String imageName) {
    return buildObjectName(comicId, chapterId, imageName);
  }

  @Override
  public boolean imageExists(String comicId, String chapterId, String imageName) {
    try {
      var objectName = buildObjectName(comicId, chapterId, imageName);
      minioClient.statObject(StatObjectArgs.builder()
          .bucket(bucketName)
          .object(objectName)
          .build());
      return true;
    } catch (Exception e) {
      return false;
    }
  }


  private String buildObjectName(String comicId, String chapterId, String imageName) {
    if (isBlank(imageName)) {
      return String.format("comics/%s/%s/", comicId, chapterId);
    }
    return String.format("comics/%s/%s/%s", comicId, chapterId, imageName);
  }

  private int delete(String prefix) throws Exception {
    var objectsList = minioClient.listObjects(ListObjectsArgs.builder()
        .bucket(bucketName)
        .prefix(prefix)
        .recursive(true)
        .build());

    var deletedCount = 0;
    for (var result : objectsList) {
      var item = result.get();
      minioClient.removeObject(RemoveObjectArgs.builder()
          .bucket(bucketName)
          .object(item.objectName())
          .build());
      deletedCount++;
    }

    return deletedCount;
  }
}
