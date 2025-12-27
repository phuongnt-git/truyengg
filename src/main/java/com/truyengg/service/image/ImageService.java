package com.truyengg.service.image;

import com.truyengg.model.dto.CompressedImageResult;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import static com.truyengg.domain.constant.AppConstants.CONTENT_TYPE_JPEG;
import static com.truyengg.domain.constant.AppConstants.CONTENT_TYPE_PNG;
import static com.truyengg.domain.constant.AppConstants.CONTENT_TYPE_WEBP;
import static com.truyengg.domain.constant.AppConstants.FORMAT_JPEG;
import static com.truyengg.domain.constant.AppConstants.FORMAT_WEBP;
import static com.truyengg.domain.constant.AppConstants.GRAYSCALE_SAMPLE_SIZE;
import static com.truyengg.domain.constant.AppConstants.GRAYSCALE_THRESHOLD;
import static java.awt.RenderingHints.KEY_ANTIALIASING;
import static java.awt.RenderingHints.KEY_INTERPOLATION;
import static java.awt.RenderingHints.KEY_RENDERING;
import static java.awt.RenderingHints.VALUE_ANTIALIAS_ON;
import static java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR;
import static java.awt.RenderingHints.VALUE_RENDER_QUALITY;
import static java.awt.color.ColorSpace.CS_GRAY;
import static java.awt.color.ColorSpace.getInstance;
import static java.awt.image.BufferedImage.TYPE_BYTE_GRAY;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static java.lang.Math.abs;
import static java.lang.Math.min;
import static javax.imageio.ImageIO.createImageOutputStream;
import static javax.imageio.ImageIO.getImageWritersByFormatName;
import static javax.imageio.ImageIO.read;
import static javax.imageio.ImageWriteParam.MODE_EXPLICIT;
import static org.apache.commons.io.IOUtils.EMPTY_BYTE_ARRAY;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;

/**
 * Service for image processing: compression, format detection, resize, and blurhash generation.
 * Optimized for manga/comics with WebP support and intelligent grayscale detection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ImageService {

  final BlurHashService blurHashService;

  @Value("${truyengg.image.compression.enabled:true}")
  boolean compressionEnabled;

  @Value("${truyengg.image.compression.output-format:webp}")
  String outputFormat;

  @Value("${truyengg.image.compression.webp-quality:0.85}")
  float webpQuality;

  @Value("${truyengg.image.compression.jpeg-quality:0.90}")
  float jpegQuality;

  @Value("${truyengg.image.compression.remove-metadata:true}")
  boolean removeMetadata;

  @Value("${truyengg.image.compression.grayscale-detection:true}")
  boolean grayscaleDetection;

  @Value("${truyengg.image.compression.grayscale-quality:0.80}")
  float grayscaleQuality;

  @Value("${truyengg.image.compression.max-width:1200}")
  int maxWidth;

  @Value("${truyengg.image.compression.resize-enabled:true}")
  boolean resizeEnabled;

  /**
   * Compress and convert an image to the configured output format.
   * Auto-detects actual image format from magic bytes.
   * Applies resize if image exceeds max-width.
   * Generates blurhash for preview placeholder.
   * Uses intelligent grayscale detection for optimal quality settings.
   */
  public CompressedImageResult compressAndConvertImage(byte[] imageBytes, String fallbackContentType) {
    if (!compressionEnabled || imageBytes == null || imageBytes.length == 0) {
      return new CompressedImageResult(imageBytes, fallbackContentType,
          imageBytes != null ? imageBytes.length : 0,
          imageBytes != null ? imageBytes.length : 0, 1.0, null);
    }

    // Auto-detect actual format from magic bytes
    var detectedFormat = detectFormat(imageBytes);
    var originalContentType = "application/octet-stream".equals(detectedFormat)
        ? fallbackContentType
        : detectedFormat;

    try {
      var originalSize = imageBytes.length;
      var bufferedImage = read(new ByteArrayInputStream(imageBytes));

      if (bufferedImage == null) {
        return new CompressedImageResult(imageBytes, originalContentType, originalSize, originalSize, 1.0, null);
      }

      // Resize if too large
      if (resizeEnabled && bufferedImage.getWidth() > maxWidth) {
        bufferedImage = resizeImage(bufferedImage, maxWidth);
      }

      // Generate blurhash from resized image
      var blurhash = blurHashService.encode(bufferedImage);

      // Detect grayscale for optimal quality
      var isGrayscale = grayscaleDetection && isGrayscale(bufferedImage);
      var quality = isGrayscale ? grayscaleQuality : getQualityForFormat();

      // Compress to target format
      var compressedBytes = EMPTY_BYTE_ARRAY;
      var contentType = EMPTY;

      if (FORMAT_WEBP.equalsIgnoreCase(outputFormat)) {
        compressedBytes = tryConvertToWebP(bufferedImage, quality, isGrayscale);
        if (compressedBytes.length > 0) {
          contentType = CONTENT_TYPE_WEBP;
        } else {
          compressedBytes = convertToOptimizedJpeg(bufferedImage, jpegQuality);
          contentType = CONTENT_TYPE_JPEG;
        }
      } else {
        compressedBytes = convertToOptimizedJpeg(bufferedImage, quality);
        contentType = CONTENT_TYPE_JPEG;
      }

      var compressedSize = compressedBytes.length;
      var compressionRatio = (double) compressedSize / originalSize;

      return new CompressedImageResult(compressedBytes, contentType, originalSize, compressedSize, compressionRatio, blurhash);

    } catch (Exception e) {
      log.warn("Error compressing image: {}", getRootCauseMessage(e));
      return new CompressedImageResult(imageBytes, originalContentType, imageBytes.length, imageBytes.length, 1.0, null);
    }
  }

  /**
   * Detect image format from magic bytes.
   *
   * @param data image bytes
   * @return content type (e.g., "image/jpeg", "image/png")
   */
  public String detectFormat(byte[] data) {
    if (data == null || data.length < 12) {
      return "application/octet-stream";
    }

    // JPEG: FF D8
    if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) {
      return CONTENT_TYPE_JPEG;
    }

    // PNG: 89 50 4E 47
    if (data[0] == (byte) 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
      return CONTENT_TYPE_PNG;
    }

    // GIF: 47 49 46
    if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
      return "image/gif";
    }

    // WebP: RIFF....WEBP
    if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F') {
      if (data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
        return CONTENT_TYPE_WEBP;
      }
    }

    return "application/octet-stream";
  }

  /**
   * Recompress an image with a different quality.
   * Used for adaptive quality serving based on network conditions.
   */
  public byte[] recompressWithQuality(byte[] imageBytes, float quality) {
    if (imageBytes == null || imageBytes.length == 0) {
      return imageBytes;
    }

    try {
      var bufferedImage = read(new ByteArrayInputStream(imageBytes));
      if (bufferedImage == null) {
        return imageBytes;
      }

      return convertToOptimizedJpeg(bufferedImage, quality);
    } catch (Exception e) {
      log.warn("Error recompressing image: {}", getRootCauseMessage(e));
      return imageBytes;
    }
  }

  /**
   * Get file extension for content type.
   */
  public String getFileExtensionForContentType(String contentType) {
    return switch (contentType.toLowerCase()) {
      case CONTENT_TYPE_WEBP -> ".webp";
      case CONTENT_TYPE_JPEG -> ".jpeg";
      case CONTENT_TYPE_PNG -> ".png";
      case "image/gif" -> ".gif";
      default -> ".jpg";
    };
  }

  private BufferedImage resizeImage(BufferedImage original, int targetWidth) {
    var ratio = (double) targetWidth / original.getWidth();
    var targetHeight = (int) (original.getHeight() * ratio);

    var imageType = original.getType();
    if (imageType == 0) {
      imageType = TYPE_INT_RGB;
    }

    var resized = new BufferedImage(targetWidth, targetHeight, imageType);
    var g = resized.createGraphics();
    g.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BILINEAR);
    g.setRenderingHint(KEY_RENDERING, VALUE_RENDER_QUALITY);
    g.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
    g.drawImage(original, 0, 0, targetWidth, targetHeight, null);
    g.dispose();

    return resized;
  }

  private byte[] tryConvertToWebP(BufferedImage image, float quality, boolean isGrayscale) {
    try {
      var outputStream = new ByteArrayOutputStream();
      var imageWriters = getImageWritersByFormatName(FORMAT_WEBP);

      if (!imageWriters.hasNext()) {
        return EMPTY_BYTE_ARRAY;
      }

      var imageWriter = imageWriters.next();

      try (var imageOutputStream = createImageOutputStream(outputStream)) {
        imageWriter.setOutput(imageOutputStream);

        var writeParam = imageWriter.getDefaultWriteParam();
        if (writeParam.canWriteCompressed()) {
          writeParam.setCompressionMode(MODE_EXPLICIT);
          writeParam.setCompressionQuality(quality);
        }

        var processedImage = removeMetadata ? convertToRgb(image) : image;

        if (isGrayscale && removeMetadata) {
          processedImage = convertToGrayscale(processedImage);
        }

        imageWriter.write(null, new IIOImage(processedImage, null, null), writeParam);
        imageWriter.dispose();

        return outputStream.toByteArray();
      }
    } catch (Exception e) {
      log.warn("Error converting to WebP: {}", getRootCauseMessage(e));
      return EMPTY_BYTE_ARRAY;
    }
  }

  private byte[] convertToOptimizedJpeg(BufferedImage image, float quality) throws IOException {
    var outputStream = new ByteArrayOutputStream();
    var imageWriters = getImageWritersByFormatName(FORMAT_JPEG);

    if (!imageWriters.hasNext()) {
      throw new IOException("No JPEG writer available");
    }

    var imageWriter = imageWriters.next();

    try (var imageOutputStream = createImageOutputStream(outputStream)) {
      imageWriter.setOutput(imageOutputStream);

      var writeParam = imageWriter.getDefaultWriteParam();
      if (writeParam.canWriteCompressed()) {
        writeParam.setCompressionMode(MODE_EXPLICIT);
        writeParam.setCompressionQuality(quality);
        writeParam.setCompressionType("JPEG");
      }

      var iioImage = removeMetadata
          ? new IIOImage(convertToRgb(image), null, null)
          : new IIOImage(image, null, null);

      imageWriter.write(null, iioImage, writeParam);
      imageWriter.dispose();

      return outputStream.toByteArray();
    }
  }

  private boolean isGrayscale(BufferedImage image) {
    var width = image.getWidth();
    var height = image.getHeight();
    var totalPixels = width * height;
    var sampleSize = min(GRAYSCALE_SAMPLE_SIZE, totalPixels / 100);

    if (sampleSize < 10) {
      sampleSize = min(10, totalPixels);
    }

    var random = new Random(42);

    for (var i = 0; i < sampleSize; i++) {
      var x = random.nextInt(width);
      var y = random.nextInt(height);
      var rgb = image.getRGB(x, y);

      var r = (rgb >> 16) & 0xFF;
      var g = (rgb >> 8) & 0xFF;
      var b = rgb & 0xFF;

      if (abs(r - g) > GRAYSCALE_THRESHOLD ||
          abs(g - b) > GRAYSCALE_THRESHOLD ||
          abs(r - b) > GRAYSCALE_THRESHOLD) {
        return false;
      }
    }

    return true;
  }

  private BufferedImage convertToRgb(BufferedImage image) {
    if (image.getType() == TYPE_INT_RGB) {
      return image;
    }

    var rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), TYPE_INT_RGB);
    var graphics = rgbImage.createGraphics();
    graphics.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BILINEAR);
    graphics.setRenderingHint(KEY_RENDERING, VALUE_RENDER_QUALITY);
    graphics.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
    graphics.drawImage(image, 0, 0, null);
    graphics.dispose();

    return rgbImage;
  }

  private BufferedImage convertToGrayscale(BufferedImage image) {
    if (image.getType() == TYPE_BYTE_GRAY) {
      return image;
    }

    var grayImage = new BufferedImage(image.getWidth(), image.getHeight(), TYPE_BYTE_GRAY);
    var colorConvert = new ColorConvertOp(getInstance(CS_GRAY), null);
    colorConvert.filter(image, grayImage);

    return grayImage;
  }

  private float getQualityForFormat() {
    return FORMAT_WEBP.equalsIgnoreCase(outputFormat) ? webpQuality : jpegQuality;
  }
}
