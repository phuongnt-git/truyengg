package com.truyengg.controller.api.image;

import com.truyengg.domain.constant.AppConstants;
import com.truyengg.service.image.ImageService;
import com.truyengg.service.storage.ImageStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.truyengg.domain.constant.AppConstants.VALUE_CACHE_CONTROL;
import static com.truyengg.util.ImageProxyUtils.detectContentType;
import static com.truyengg.util.ImageProxyUtils.generateETag;
import static java.lang.Long.parseLong;
import static java.lang.Math.min;
import static java.lang.String.format;
import static java.util.Arrays.copyOfRange;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;
import static org.springframework.http.HttpHeaders.ACCEPT_RANGES;
import static org.springframework.http.HttpHeaders.CONTENT_RANGE;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NOT_MODIFIED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.PARTIAL_CONTENT;
import static org.springframework.http.HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE;
import static org.springframework.http.MediaType.parseMediaType;
import static org.springframework.http.ResponseEntity.status;

/**
 * Image proxy controller supporting:
 * - HTTP Range requests for progressive loading
 * - Adaptive quality based on network conditions
 * - ETag-based caching
 */
@Tag(name = "Image Proxy", description = "Image proxy APIs for storage with progressive loading support")
@RestController
@RequestMapping("/api/images/proxy")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class ImageProxyController {

  private final ImageStorageService imageStorageService;
  private final ImageService imageCompressionService;

  @Value("${truyengg.image.adaptive-quality.enabled:true}")
  private boolean adaptiveQualityEnabled;

  @Value("${truyengg.image.adaptive-quality.high:0.85}")
  private float qualityHigh;

  @Value("${truyengg.image.adaptive-quality.medium:0.75}")
  private float qualityMedium;

  @Value("${truyengg.image.adaptive-quality.low:0.60}")
  private float qualityLow;

  @GetMapping("/{comicId}/{chapterId}/{imageName:.+}")
  @Operation(summary = "Proxy stored image", description = "Proxy image from storage with Range requests and adaptive quality support")
  @Cacheable(value = "img:proxy#24h", key = "#comicId + '/' + #chapterId + '/' + #imageName + '_' + #quality")
  public ResponseEntity<Resource> proxyImage(
      @PathVariable String comicId,
      @PathVariable String chapterId,
      @PathVariable String imageName,
      @RequestParam(value = "q", defaultValue = "high") String quality,
      @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch,
      @RequestHeader(value = "Range", required = false) String rangeHeader) {
    try {
      var imageBytes = imageStorageService.getImage(comicId, chapterId, imageName);

      if (imageBytes.length == 0) {
        return status(NOT_FOUND).build();
      }

      // Apply adaptive quality if enabled and not high quality
      if (adaptiveQualityEnabled && !"high".equalsIgnoreCase(quality)) {
        imageBytes = applyAdaptiveQuality(imageBytes, quality);
      }

      var etag = generateETag(imageBytes);

      // Check If-None-Match header for conditional request
      if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
        var headers = new HttpHeaders();
        headers.setETag(etag);
        headers.setCacheControl(VALUE_CACHE_CONTROL);
        headers.set(ACCEPT_RANGES, AppConstants.BYTES);
        return status(NOT_MODIFIED).headers(headers).build();
      }

      // Handle Range requests for progressive loading
      if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
        return handleRangeRequest(imageBytes, rangeHeader, imageName, etag);
      }

      // Full content response
      var headers = buildImageHeaders(imageName, imageBytes.length, etag, true);
      return new ResponseEntity<>(new ByteArrayResource(imageBytes), headers, OK);
    } catch (Exception e) {
      log.warn("Error proxying image {}/{}/{}: {}", comicId, chapterId, imageName, getRootCauseMessage(e));
      return status(NOT_FOUND).build();
    }
  }

  /**
   * Handle HTTP Range requests for partial content delivery.
   * Enables progressive loading of large images.
   */
  private ResponseEntity<Resource> handleRangeRequest(byte[] data, String rangeHeader,
                                                      String imageName, String etag) {
    try {
      // Parse range header: bytes=start-end or bytes=start-
      var rangeSpec = rangeHeader.substring(6); // Remove "bytes="
      var ranges = rangeSpec.split("-");

      var start = parseLong(ranges[0]);
      var end = ranges.length > 1 && !ranges[1].isEmpty()
          ? parseLong(ranges[1])
          : data.length - 1;

      // Validate range
      if (start > end || start >= data.length) {
        return status(REQUESTED_RANGE_NOT_SATISFIABLE).build();
      }

      // Clamp end to data length
      end = min(end, data.length - 1);

      var partialData = copyOfRange(data, (int) start, (int) end + 1);

      var headers = buildImageHeaders(imageName, partialData.length, etag, true);
      headers.set(CONTENT_RANGE, format("bytes %d-%d/%d", start, end, data.length));

      return new ResponseEntity<>(new ByteArrayResource(partialData), headers, PARTIAL_CONTENT);
    } catch (Exception e) {
      log.warn("Error handling range request: {}", getRootCauseMessage(e));
      // Fall back to full content
      var headers = buildImageHeaders(imageName, data.length, etag, false);
      return new ResponseEntity<>(new ByteArrayResource(data), headers, OK);
    }
  }

  private HttpHeaders buildImageHeaders(String imageName, long contentLength, String etag, boolean acceptRanges) {
    var headers = new HttpHeaders();
    headers.setContentType(parseMediaType(detectContentType(imageName)));
    headers.setContentLength(contentLength);
    headers.setCacheControl(VALUE_CACHE_CONTROL);
    headers.setETag(etag);
    if (acceptRanges) {
      headers.set(ACCEPT_RANGES, AppConstants.BYTES);
    }
    return headers;
  }

  /**
   * Apply adaptive quality based on network conditions.
   *
   * @param imageBytes original image bytes
   * @param quality    quality level (high, medium, low)
   * @return recompressed image bytes
   */
  private byte[] applyAdaptiveQuality(byte[] imageBytes, String quality) {
    var qualityValue = switch (quality.toLowerCase()) {
      case "low" -> qualityLow;
      case "medium" -> qualityMedium;
      default -> qualityHigh;
    };

    return imageCompressionService.recompressWithQuality(imageBytes, qualityValue);
  }
}


