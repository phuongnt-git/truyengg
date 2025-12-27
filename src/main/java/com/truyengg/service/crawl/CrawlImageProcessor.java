package com.truyengg.service.crawl;

import com.truyengg.service.image.ImageService;
import com.truyengg.service.storage.ImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static com.truyengg.domain.constant.AppConstants.ATTR_DATA_ORIGINAL;
import static com.truyengg.domain.constant.AppConstants.ATTR_DATA_SRC;
import static com.truyengg.domain.constant.AppConstants.ATTR_SRC;
import static com.truyengg.domain.constant.AppConstants.PREFIX_DATA_URI;
import static com.truyengg.domain.constant.AppConstants.PROTOCOL_HTTPS;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Image processing utilities for crawling.
 * Handles URL normalization, extraction from HTML, and upload to storage.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlImageProcessor {

  private final ImageStorageService imageStorageService;
  private final ImageService imageService;

  /**
   * Normalize image URL to absolute URL.
   */
  public String normalizeImageUrl(String imgUrl, String domain) {
    if (imgUrl.startsWith("//")) {
      return PROTOCOL_HTTPS + imgUrl;
    } else if (imgUrl.startsWith("/")) {
      return domain + imgUrl;
    }
    return imgUrl;
  }

  /**
   * Extract image URLs from HTML document.
   */
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

  /**
   * Process and upload an image to storage.
   * Returns the storage path and blurhash in the result.
   */
  public ImageUploadResult processAndUpload(byte[] imageBytes, String comicSlug, String chapterId, String fileName) {
    var compressionResult = imageService.compressAndConvertImage(imageBytes, "image/jpeg");
    var compressedBytes = compressionResult.compressedBytes();
    var contentType = compressionResult.contentType();
    var blurhash = compressionResult.blurhash();
    var finalFileName = updateFileNameWithExtension(fileName, contentType);

    imageStorageService.uploadImage(comicSlug, chapterId, finalFileName, compressedBytes, contentType);
    var path = imageStorageService.getImagePath(comicSlug, chapterId, finalFileName);

    return new ImageUploadResult(path, blurhash);
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

  // ===== Private methods =====

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
    return isBlank(url) ||
        url.contains("logo") || url.contains("icon") || url.contains("avatar") ||
        url.contains("banner") || url.contains("ad");
  }

  private String updateFileNameWithExtension(String fileName, String contentType) {
    var fileExtension = imageService.getFileExtensionForContentType(contentType);
    var lastDotIndex = fileName.lastIndexOf('.');
    if (lastDotIndex > 0) {
      return fileName.substring(0, lastDotIndex) + fileExtension;
    }
    return fileName + fileExtension;
  }

  /**
   * Result of image upload containing path and blurhash.
   */
  public record ImageUploadResult(String path, String blurhash) {
  }
}
