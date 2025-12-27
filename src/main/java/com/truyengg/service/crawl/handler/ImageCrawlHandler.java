package com.truyengg.service.crawl.handler;

import com.truyengg.domain.entity.CrawlJob;
import com.truyengg.domain.enums.CrawlType;
import com.truyengg.domain.repository.CrawlSettingsRepository;
import com.truyengg.service.crawl.CrawlCheckpointService;
import com.truyengg.service.crawl.CrawlHttpClient;
import com.truyengg.service.crawl.CrawlImageProcessor;
import com.truyengg.service.crawl.CrawlImageProcessor.ImageUploadResult;
import com.truyengg.service.crawl.CrawlJobService;
import com.truyengg.service.crawl.CrawlProgressService;
import com.truyengg.service.crawl.CrawlQueueProcessor;
import com.truyengg.service.crawl.DownloadModeService;
import com.truyengg.service.crawl.PauseStateService;
import com.truyengg.service.crawl.SlugExtractor;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

import static com.truyengg.domain.enums.CrawlSourceType.detectSourceFromUrl;
import static com.truyengg.domain.enums.CrawlType.IMAGE;
import static java.util.Collections.emptyMap;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;

/**
 * Handler for IMAGE type crawl jobs.
 * Downloads a single image, compresses it, generates blurhash, and stores metadata.
 * Uses metadata stored by parent CHAPTER job when available.
 */
@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ImageCrawlHandler extends AbstractCrawlHandler {

  CrawlImageProcessor imageProcessor;

  public ImageCrawlHandler(
      CrawlJobService jobService,
      CrawlProgressService progressService,
      CrawlCheckpointService checkpointService,
      CrawlHttpClient httpClient,
      CrawlHandlerFactory handlerFactory,
      PauseStateService pauseStateService,
      DownloadModeService downloadModeService,
      CrawlSettingsRepository settingsRepository,
      CrawlQueueProcessor queueProcessor,
      CrawlImageProcessor imageProcessor) {
    super(jobService, progressService, checkpointService, httpClient, handlerFactory, pauseStateService, downloadModeService, settingsRepository, queueProcessor);
    this.imageProcessor = imageProcessor;
  }

  @Override
  public void handleWithResume(CrawlJob job, int startIndex) {
    // IMAGE jobs don't support resume - they're atomic
    var jobId = job.getId();

    var sourceType = detectSourceFromUrl(job.getTargetUrl());
    var handler = handlerFactory.getHandler(sourceType);
    var domain = handler.extractDomainFromUrl(job.getTargetUrl());

    // Get metadata from checkpoint (stored by parent CHAPTER job)
    var metadata = getImageMetadata(job);
    var comicSlug = getStringFromMetadata(metadata, "comicSlug", extractSlug(job));
    var chapterId = getStringFromMetadata(metadata, "chapterId", determineChapterId(job));
    var fileName = getStringFromMetadata(metadata, "fileName", determineFileName(job));
    var imageIndex = getIntFromMetadata(metadata, "imageIndex", job.getItemIndex());

    setTotalItems(jobId, 1);
    updateMessage(jobId, "Downloading image: " + job.getTargetUrl());

    try {
      var result = downloadAndProcessImage(job.getTargetUrl(), comicSlug, chapterId, fileName, domain);

      // Store result metadata including blurhash
      storeImageResult(jobId, imageIndex, job.getTargetUrl(), result);

      incrementCompleted(jobId);
      updateMessage(jobId, "Image downloaded: " + result.path());

      // Update parent CHAPTER job progress
      updateParentProgress(job);

    } catch (Exception e) {
      log.warn("Failed to download image for job {}: {}", jobId, getRootCauseMessage(e));
      incrementFailed(jobId);
      updateMessage(jobId, "Failed: " + getRootCauseMessage(e));

      // Store failure in checkpoint
      storeImageFailure(jobId, imageIndex, job.getTargetUrl(), getRootCauseMessage(e));

      // Propagate failure to parent
      propagateFailureToParent(job, imageIndex, getRootCauseMessage(e));
    }
  }

  @Override
  public CrawlType getSupportedType() {
    return IMAGE;
  }

  // ===== Private methods =====

  /**
   * Download, compress, generate blurhash, and store image.
   */
  private ImageUploadResult downloadAndProcessImage(String imageUrl, String comicSlug, String chapterId,
                                                    String fileName, String domain) {
    var headers = httpClient.buildHeaders(domain);
    var imageBytes = httpClient.downloadImage(imageUrl, headers);

    if (imageBytes == null || imageBytes.length == 0) {
      throw new IllegalStateException("Failed to download image: " + imageUrl);
    }

    // Process image: compress, convert to webp, generate blurhash
    return imageProcessor.processAndUpload(imageBytes, comicSlug, chapterId, fileName);
  }

  /**
   * Get metadata stored by parent CHAPTER job.
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> getImageMetadata(CrawlJob job) {
    try {
      var state = checkpointService.getState(job.getId(), "imageMetadata", Map.class);
      if (state != null) {
        return (Map<String, Object>) state;
      }
    } catch (Exception e) {
      log.warn("No image metadata found for job {}: {}", job.getId(), e.getMessage());
    }
    return emptyMap();
  }

  private String getStringFromMetadata(Map<String, Object> metadata, String key, String defaultValue) {
    var value = metadata.get(key);
    if (value instanceof String str) {
      return defaultIfBlank(str, defaultValue);
    }
    return defaultValue;
  }

  private int getIntFromMetadata(Map<String, Object> metadata, String key, int defaultValue) {
    var value = metadata.get(key);
    if (value instanceof Number n) {
      return n.intValue();
    }
    return defaultValue;
  }

  /**
   * Store successful image download result.
   */
  private void storeImageResult(java.util.UUID jobId, int imageIndex, String originalUrl, ImageUploadResult result) {
    var resultData = Map.of(
        "imageIndex", imageIndex,
        "originalUrl", originalUrl,
        "path", result.path(),
        "blurhash", defaultIfBlank(result.blurhash(), EMPTY),
        "status", "SUCCESS"
    );
    checkpointService.saveState(jobId, "downloadedImage", resultData);
  }

  /**
   * Store image download failure.
   */
  private void storeImageFailure(UUID jobId, int imageIndex, String originalUrl, String error) {
    var resultData = Map.of(
        "imageIndex", imageIndex,
        "originalUrl", originalUrl,
        "error", error,
        "status", "FAILED"
    );
    checkpointService.saveState(jobId, "downloadedImage", resultData);
  }

  /**
   * Update parent CHAPTER job progress after image download.
   */
  private void updateParentProgress(CrawlJob job) {
    var parentJob = job.getParentJob();
    if (parentJob == null || parentJob.getCrawlType() != CrawlType.CHAPTER) {
      return;
    }

    try {
      progressService.incrementCompleted(parentJob.getId());
      checkpointService.updateLastItemIndex(parentJob.getId(), job.getItemIndex());
    } catch (Exception e) {
      log.warn("Failed to update parent job {} progress: {}", parentJob.getId(), e.getMessage());
    }
  }

  /**
   * Propagate failure to parent CHAPTER job.
   */
  private void propagateFailureToParent(CrawlJob job, int imageIndex, String error) {
    var parentJob = job.getParentJob();
    if (parentJob == null || parentJob.getCrawlType() != CrawlType.CHAPTER) {
      return;
    }

    try {
      progressService.incrementFailed(parentJob.getId(), error);
      checkpointService.addFailedIndex(parentJob.getId(), imageIndex);
    } catch (Exception e) {
      log.warn("Failed to propagate failure to parent job {}: {}", parentJob.getId(), e.getMessage());
    }
  }

  /**
   * Determine chapter ID from job hierarchy.
   */
  private String determineChapterId(CrawlJob job) {
    // If parent is CHAPTER type, use parent's item index
    if (job.getParentJob() != null && job.getParentJob().getCrawlType() == CrawlType.CHAPTER) {
      return "chapter-" + job.getParentJob().getItemIndex();
    }
    // Default to job's own depth or "images" directory
    return job.getDepth() > 0 ? "chapter-" + job.getItemIndex() : "images";
  }

  /**
   * Determine file name from job.
   */
  private String determineFileName(CrawlJob job) {
    // Use item index if available
    if (job.getItemIndex() > 0) {
      return "image-%03d.webp".formatted(job.getItemIndex());
    }
    // Extract from URL or use default
    var slug = SlugExtractor.extractSlugFromUrl(job.getTargetUrl());
    return slug != null ? slug + ".webp" : "image.webp";
  }
}
