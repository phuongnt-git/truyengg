package com.truyengg.util;

import com.truyengg.domain.repository.ChapterCrawlRepository;
import com.truyengg.service.MinioService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
public class FileStorageUtil {

  private static final Pattern INVALID_FILENAME_CHARS = Pattern.compile("[\\\\/*:\"<>|?]");

  /**
   * Tạo thư mục nếu chưa tồn tại
   */
  public static Path createDirectory(Path path) throws IOException {
    if (!Files.exists(path)) {
      Files.createDirectories(path);
      log.debug("Created directory: {}", path);
    }
    return path;
  }

  /**
   * Lưu file với nội dung byte array
   */
  public static void saveFile(byte[] content, Path filePath) throws IOException {
    createDirectory(filePath.getParent());
    Files.write(filePath, content);
    log.debug("Saved file: {}", filePath);
  }

  /**
   * Làm sạch tên file, loại bỏ các ký tự không hợp lệ
   */
  public static String sanitizeFilename(String filename) {
    if (StringUtils.isBlank(filename)) {
      return "unnamed";
    }

    // Loại bỏ các ký tự không hợp lệ cho tên file
    String sanitized = INVALID_FILENAME_CHARS.matcher(filename).replaceAll("");

    // Loại bỏ khoảng trắng ở đầu và cuối
    sanitized = sanitized.trim();

    // Nếu sau khi sanitize trống, trả về tên mặc định
    if (sanitized.isEmpty()) {
      return "unnamed";
    }

    return sanitized;
  }

  /**
   * Tạo Path từ string, đảm bảo thư mục tồn tại
   */
  public static Path getOrCreatePath(String pathString) throws IOException {
    Path path = Paths.get(pathString);
    createDirectory(path.getParent());
    return path;
  }

  /**
   * Xóa toàn bộ files và thư mục của một job
   * Deletes all images from MinIO that were uploaded by this crawl job.
   *
   * @param jobId                    Job ID
   * @param downloadDir              Download directory root (not used for MinIO, kept for compatibility)
   * @param minioService             MinioService instance
   * @param crawlJobDetailRepository CrawlJobDetailRepository instance
   */
  public static void deleteJobFiles(UUID jobId, String downloadDir,
                                    MinioService minioService,
                                    ChapterCrawlRepository chapterCrawlRepository) {
    if (minioService == null || chapterCrawlRepository == null) {
      log.warn("MinioService or ChapterCrawlRepository not provided, cannot delete job files for job {}", jobId);
      return;
    }

    try {
      // Get all job details for this job
      var jobDetails = chapterCrawlRepository.findByCrawlIdOrderByChapterIndex(jobId);

      if (jobDetails.isEmpty()) {
        log.debug("No job details found for job {}, nothing to delete", jobId);
        return;
      }

      int deletedCount = 0;
      for (var detail : jobDetails) {
        // Extract MinIO paths from detail
        var imagePaths = detail.getImagePaths();
        if (imagePaths != null && !imagePaths.isEmpty()) {
          for (var path : imagePaths) {
            try {
              // Parse path: comics/{comicId}/{chapterId}/{imageName}
              var parts = path.split("/");
              if (parts.length >= 4 && "comics".equals(parts[0])) {
                var comicId = parts[1];
                var chapterId = parts[2];
                var imageName = parts[3];
                minioService.deleteImage(comicId, chapterId, imageName);
                deletedCount++;
              }
            } catch (Exception e) {
              log.warn("Failed to delete image from MinIO path {}: {}", path, e.getMessage());
            }
          }
        }
      }

      log.info("Deleted {} images from MinIO for job {}", deletedCount, jobId);
    } catch (Exception e) {
      log.error("Error deleting job files for job {}: {}", jobId, e.getMessage(), e);
      // Don't throw exception - allow operation to continue even if deletion fails
    }
  }

  /**
   * Xóa đệ quy một thư mục và tất cả nội dung bên trong
   */
  public static void deleteDirectoryRecursively(Path directory) throws IOException {
    if (!Files.exists(directory)) {
      return;
    }

    Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        if (exc != null) {
          throw exc;
        }
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }
}

