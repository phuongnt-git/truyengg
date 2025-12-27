package com.truyengg.controller.api;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
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
import org.springframework.web.bind.annotation.RestController;

import static com.truyengg.service.crawl.CrawlConstants.VALUE_CACHE_CONTROL;
import static com.truyengg.util.ImageProxyUtils.detectContentType;
import static com.truyengg.util.ImageProxyUtils.generateETag;
import static com.truyengg.util.ImageProxyUtils.parseHttpDate;
import static java.lang.String.format;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NOT_MODIFIED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.parseMediaType;
import static org.springframework.http.ResponseEntity.status;

@Tag(name = "Image Proxy", description = "Image proxy APIs for MinIO")
@RestController
@RequestMapping("/api/images/proxy")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class ImageProxyController {

  private final MinioClient minioClient;

  @Value("${truyengg.minio.bucket-name:truyengg-images}")
  private String bucketName;

  @GetMapping("/{comicId}/{chapterId}/{imageName:.+}")
  @Operation(summary = "Proxy MinIO image", description = "Proxy image from MinIO to avoid CORS/403 issues")
  @Cacheable(value = "img:proxy#24h", key = "#comicId + '/' + #chapterId + '/' + #imageName")
  public ResponseEntity<Resource> proxyImage(
      @PathVariable String comicId,
      @PathVariable String chapterId,
      @PathVariable String imageName,
      @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch,
      @RequestHeader(value = "If-Modified-Since", required = false) String ifModifiedSince) {
    try {
      var objectName = format("comics/%s/%s/%s", comicId, chapterId, imageName);

      var statObject = minioClient.statObject(StatObjectArgs.builder()
          .bucket(bucketName)
          .object(objectName)
          .build());

      // Check If-Modified-Since header for conditional request
      if (ifModifiedSince != null && statObject != null && statObject.lastModified() != null) {
        var lastModifiedInstant = statObject.lastModified().toInstant();
        var ifModifiedSinceInstant = parseHttpDate(ifModifiedSince);

        // If object hasn't been modified since the requested date, return 304
        if (ifModifiedSinceInstant != null && !lastModifiedInstant.isAfter(ifModifiedSinceInstant)) {
          var headers = new HttpHeaders();
          headers.setLastModified(lastModifiedInstant.toEpochMilli());
          headers.setCacheControl(VALUE_CACHE_CONTROL);
          return status(NOT_MODIFIED).headers(headers).build();
        }
      }

      var imageBytes = new byte[0];
      try (var objectStream = minioClient.getObject(GetObjectArgs.builder()
          .bucket(bucketName)
          .object(objectName)
          .build())) {
        imageBytes = objectStream.readAllBytes();
      }

      var etag = generateETag(imageBytes);
      // Check If-None-Match header for conditional request
      if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
        var headers = new HttpHeaders();
        headers.setETag(etag);
        headers.setCacheControl(VALUE_CACHE_CONTROL);
        if (statObject != null && statObject.lastModified() != null) {
          headers.setLastModified(statObject.lastModified().toInstant().toEpochMilli());
        }
        return status(NOT_MODIFIED).headers(headers).build();
      }

      var headers = new HttpHeaders();
      var contentType = detectContentType(imageName);
      headers.setContentType(parseMediaType(contentType));
      headers.setContentLength(imageBytes.length);
      headers.setCacheControl(VALUE_CACHE_CONTROL);
      headers.setETag(etag);

      if (statObject != null && statObject.lastModified() != null) {
        headers.setLastModified(statObject.lastModified().toInstant().toEpochMilli());
      }

      // Use ByteArrayResource for better streaming support and graceful handling of client disconnections
      var resource = new ByteArrayResource(imageBytes);
      return new ResponseEntity<>(resource, headers, OK);
    } catch (Exception e) {
      return status(NOT_FOUND).build();
    }
  }

}

