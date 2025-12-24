package com.truyengg.service.crawl;

import com.truyengg.model.dto.ChapterCrawlProcessingParams;
import com.truyengg.model.dto.ImageProcessResult;
import com.truyengg.model.dto.ImageProcessingContext;
import com.truyengg.model.dto.ProcessedImage;
import com.truyengg.model.response.ChapterCrawlProgress;
import com.truyengg.service.ImageCompressionService;
import com.truyengg.service.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static com.truyengg.domain.enums.ChapterCrawlStatus.DOWNLOADING;
import static com.truyengg.service.crawl.CrawlConstants.ATTR_DATA_ORIGINAL;
import static com.truyengg.service.crawl.CrawlConstants.ATTR_DATA_SRC;
import static com.truyengg.service.crawl.CrawlConstants.ATTR_SRC;
import static com.truyengg.service.crawl.CrawlConstants.PREFIX_DATA_URI;
import static com.truyengg.service.crawl.CrawlConstants.PROTOCOL_HTTPS;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
@RequiredArgsConstructor
@Slf4j
public class CrawlImageProcessor {

  private final MinioService minioService;
  private final CrawlHttpClient crawlHttpClient;
  private final ImageCompressionService imageCompressionService;
  private final ProgressMessagePublisher progressMessagePublisher;

  public String normalizeImageUrl(String imgUrl, String domain) {
    if (imgUrl.startsWith("//")) {
      return PROTOCOL_HTTPS + imgUrl;
    } else if (imgUrl.startsWith("/")) {
      return domain + imgUrl;
    }
    return imgUrl;
  }

  public List<String> extractImageUrlsFromHtml(Document doc, String domain) {
    var imageUrls = new ArrayList<String>();

    if (extractFromPrimarySelector(doc, imageUrls)) {
      return cleanImageUrls(imageUrls);
    }

    if (extractFromSecondarySelector(doc, imageUrls)) {
      return cleanImageUrls(imageUrls);
    }

    if (extractFromCommonSelectors(doc, imageUrls, domain)) {
      return cleanImageUrls(imageUrls);
    }

    extractFromContainers(doc, imageUrls, domain);
    return cleanImageUrls(imageUrls);
  }

  private boolean extractFromPrimarySelector(Document doc, List<String> imageUrls) {
    var imgElements = doc.select("div.page-chapter img[" + ATTR_DATA_ORIGINAL + "]");
    for (var img : imgElements) {
      var imgUrl = img.attr(ATTR_DATA_ORIGINAL);
      if (isNotBlank(imgUrl)) {
        imageUrls.add(imgUrl);
      }
    }
    return !imageUrls.isEmpty();
  }

  private boolean extractFromSecondarySelector(Document doc, List<String> imageUrls) {
    var imgElements = doc.select("div.page-chapter img[" + ATTR_SRC + "]");
    for (var img : imgElements) {
      var imgUrl = img.attr(ATTR_SRC);
      if (isNotBlank(imgUrl) && !imgUrl.startsWith(PREFIX_DATA_URI)) {
        imageUrls.add(imgUrl);
      }
    }
    return !imageUrls.isEmpty();
  }

  private boolean extractFromCommonSelectors(Document doc, List<String> imageUrls, String domain) {
    var imgElements = doc.select("div.reading-content img, div.chapter-content img, div.viewer img, .viewer img");
    for (var img : imgElements) {
      var imgUrl = extractImageUrlFromElement(img);
      if (isValidImageUrl(imgUrl)) {
        imageUrls.add(normalizeImageUrl(imgUrl, domain));
      }
    }
    return !imageUrls.isEmpty();
  }

  private void extractFromContainers(Document doc, List<String> imageUrls, String domain) {
    var containers = doc.select("div[class*='page'], div[class*='chapter'], div[class*='viewer'], div[class*='reading'], div[class*='content']");
    for (var container : containers) {
      var imgs = container.select("img");
      for (var img : imgs) {
        var imgUrl = extractImageUrlFromElement(img);
        if (isValidImageUrlForContainer(imgUrl)) {
          imageUrls.add(normalizeImageUrl(imgUrl, domain));
        }
      }
    }
  }

  private String extractImageUrlFromElement(Element img) {
    var imgUrl = img.attr(ATTR_DATA_ORIGINAL);
    if (isEmpty(imgUrl)) {
      imgUrl = img.attr(ATTR_DATA_SRC);
    }
    if (isEmpty(imgUrl)) {
      imgUrl = img.attr(ATTR_SRC);
    }
    return imgUrl;
  }

  private boolean isValidImageUrl(String imgUrl) {
    return isNotBlank(imgUrl) && !imgUrl.startsWith(PREFIX_DATA_URI) &&
        (imgUrl.contains("http") || imgUrl.startsWith("//") || imgUrl.startsWith("/"));
  }

  private boolean isValidImageUrlForContainer(String imgUrl) {
    return isValidImageUrl(imgUrl) &&
        !imgUrl.contains("logo") && !imgUrl.contains("icon") && !imgUrl.contains("avatar");
  }

  private List<String> cleanImageUrls(List<String> imageUrls) {
    var uniqueUrls = new ArrayList<>(new LinkedHashSet<>(imageUrls));
    uniqueUrls.removeIf(this::isInvalidUrl);
    return uniqueUrls;
  }

  private boolean isInvalidUrl(String url) {
    return url == null || url.isEmpty() ||
        url.contains("logo") || url.contains("icon") || url.contains("avatar") ||
        url.contains("banner") || url.contains("ad");
  }

  /**
   * Process a single image: download and upload to MinIO
   * Updates chapterCrawlProgress in params but does not call updateProgress (caller should do that)
   *
   * @return ImageProcessResult containing success status, file size, and request/error counts
   */
  public ImageProcessResult processImageWithProgress(
      ImageProcessingContext context,
      ChapterCrawlProcessingParams params,
      List<String> imagePaths) {
    var imageBytes = crawlHttpClient.downloadImage(context.imageUrl(), context.headers());
    if (imageBytes == null || imageBytes.length == 0) {
      return new ImageProcessResult(0, 0L, 1, 1);
    }

    try {
      var processedImage = processAndUploadImage(imageBytes, context, imagePaths);
      updateProgress(context, params);
      return new ImageProcessResult(1, processedImage.fileSizeBytes(), 1, 0);
    } catch (Exception e) {
      log.error("Failed to upload image to MinIO: {}", context.fileName(), e);
      progressMessagePublisher.publishMessage(params.crawlId(), "Error uploading image " + context.fileName() + ": " + e.getMessage());
      return new ImageProcessResult(0, 0L, 1, 1);
    }
  }

  private ProcessedImage processAndUploadImage(byte[] imageBytes, ImageProcessingContext context, List<String> imagePaths) {
    var compressionResult = imageCompressionService.compressAndConvertImage(imageBytes, "image/jpeg");
    var compressedBytes = compressionResult.compressedBytes();
    var contentType = compressionResult.contentType();
    var fileName = updateFileNameWithExtension(context.fileName(), contentType);

    minioService.uploadImage(context.comicId(), context.chapterId(), fileName, compressedBytes, contentType);
    var imagePath = minioService.getImagePath(context.comicId(), context.chapterId(), fileName);
    imagePaths.add(imagePath);

    return new ProcessedImage(fileName, compressionResult.compressedSize());
  }

  private String updateFileNameWithExtension(String fileName, String contentType) {
    var fileExtension = imageCompressionService.getFileExtensionForContentType(contentType);
    var lastDotIndex = fileName.lastIndexOf('.');
    if (lastDotIndex > 0) {
      return fileName.substring(0, lastDotIndex) + fileExtension;
    }
    return fileName + fileExtension;
  }

  private void updateProgress(ImageProcessingContext context, ChapterCrawlProcessingParams params) {
    var newDownloadedCount = context.currentDownloadedCount() + 1;
    var currentChapter = context.chapterIndex() + 1;
    progressMessagePublisher.publishMessage(params.crawlId(), String.format("Downloaded image %d/%d of chapter %d: %s",
        newDownloadedCount, context.totalImages(), currentChapter, context.imageUrl()));

    params.chapterProgress().put(params.chapterKey(), new ChapterCrawlProgress(
        context.chapterIndex(), params.url(), newDownloadedCount, context.totalImages(), DOWNLOADING
    ));
  }

}

