package com.truyengg.controller.api.image;

import com.truyengg.service.image.ImageService;
import com.truyengg.service.crawl.CrawlHttpClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import static com.truyengg.util.ImageProxyUtils.extractDomain;
import static com.truyengg.util.ImageProxyUtils.generateETag;
import static com.truyengg.util.ImageProxyUtils.parseHttpDate;
import static java.net.URLDecoder.decode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Instant.now;
import static java.util.Base64.getUrlDecoder;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NOT_MODIFIED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.parseMediaType;
import static org.springframework.http.ResponseEntity.status;

@Tag(name = "Original Image Proxy", description = "Original image proxy APIs with referer header support")
@RestController
@RequestMapping("/api/images/original-proxy")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class OriginalImageProxyController {

  private final CrawlHttpClient crawlHttpClient;
  private final ImageService imageCompressionService;

  @GetMapping("/{encodedUrl:.+}")
  @Operation(summary = "Proxy original image", description = "Proxy original image with referer header to avoid CORS/403 issues")
  @Cacheable(value = "originalImageCache", key = "#encodedUrl")
  public ResponseEntity<Resource> proxyOriginalImage(
      @PathVariable String encodedUrl,
      @RequestParam(required = false) String referer,
      @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch,
      @RequestHeader(value = "If-Modified-Since", required = false) String ifModifiedSince) {
    try {
      // Decode the URL
      var decodedUrl = decode(encodedUrl, UTF_8);

      // If URL is base64 encoded, decode it
      if (!decodedUrl.startsWith("http://") && !decodedUrl.startsWith("https://")) {
        decodedUrl = new String(getUrlDecoder().decode(encodedUrl), UTF_8);
      }

      // Build headers with referer if provided
      var headers = crawlHttpClient.buildHeaders(referer != null ? referer : extractDomain(decodedUrl));

      // Download image with referer header
      var imageBytes = crawlHttpClient.downloadImage(decodedUrl, headers);
      if (imageBytes == null || imageBytes.length == 0) {
        return status(NOT_FOUND).build();
      }

      // Compress image for storage optimization
      var compressionResult = imageCompressionService.compressAndConvertImage(imageBytes, "image/jpeg");
      var compressedBytes = compressionResult.compressedBytes();
      var contentType = compressionResult.contentType();

      var etag = generateETag(compressedBytes);
      var lastModified = now();

      // Check If-Modified-Since header for conditional request
      if (ifModifiedSince != null) {
        var ifModifiedSinceInstant = parseHttpDate(ifModifiedSince);
        if (ifModifiedSinceInstant != null && !lastModified.isAfter(ifModifiedSinceInstant)) {
          var notModifiedHeaders = new HttpHeaders();
          notModifiedHeaders.setLastModified(lastModified.toEpochMilli());
          notModifiedHeaders.setCacheControl(VALUE_CACHE_CONTROL);
          notModifiedHeaders.setETag(etag);
          return status(NOT_MODIFIED).headers(notModifiedHeaders).build();
        }
      }

      // Check If-None-Match header for conditional request
      if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
        var notModifiedHeaders = new HttpHeaders();
        notModifiedHeaders.setETag(etag);
        notModifiedHeaders.setCacheControl(VALUE_CACHE_CONTROL);
        notModifiedHeaders.setLastModified(lastModified.toEpochMilli());
        return status(NOT_MODIFIED).headers(notModifiedHeaders).build();
      }

      var responseHeaders = new HttpHeaders();
      responseHeaders.setContentType(parseMediaType(contentType));
      responseHeaders.setContentLength(compressedBytes.length);
      responseHeaders.setCacheControl(VALUE_CACHE_CONTROL);
      responseHeaders.setETag(etag);
      responseHeaders.setLastModified(lastModified.toEpochMilli());

      // Use ByteArrayResource for better streaming support and graceful handling of client disconnections
      var resource = new ByteArrayResource(compressedBytes);
      return new ResponseEntity<>(resource, responseHeaders, OK);
    } catch (Exception e) {
      return status(NOT_FOUND).build();
    }
  }

}

