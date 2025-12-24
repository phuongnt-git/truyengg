package com.truyengg.service;

import com.truyengg.model.dto.CompressedImageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static java.awt.RenderingHints.KEY_ANTIALIASING;
import static java.awt.RenderingHints.KEY_INTERPOLATION;
import static java.awt.RenderingHints.KEY_RENDERING;
import static java.awt.RenderingHints.VALUE_ANTIALIAS_ON;
import static java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR;
import static java.awt.RenderingHints.VALUE_RENDER_QUALITY;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static javax.imageio.ImageIO.createImageOutputStream;
import static javax.imageio.ImageIO.getImageWritersByFormatName;
import static javax.imageio.ImageIO.read;
import static javax.imageio.ImageWriteParam.MODE_EXPLICIT;

@Service
@Slf4j
public class ImageCompressionService {

  @Value("${truyengg.image.compression.enabled:true}")
  private boolean compressionEnabled;

  @Value("${truyengg.image.compression.jpeg-quality:0.90}")
  private float jpegQuality;

  @Value("${truyengg.image.compression.remove-metadata:true}")
  private boolean removeMetadata;

  public CompressedImageResult compressAndConvertImage(byte[] imageBytes, String originalContentType) {
    if (!compressionEnabled || imageBytes == null || imageBytes.length == 0) {
      return new CompressedImageResult(imageBytes, originalContentType, imageBytes != null ? imageBytes.length : 0,
          imageBytes != null ? imageBytes.length : 0, 1.0);
    }

    try {
      var originalSize = imageBytes.length;
      var inputStream = new ByteArrayInputStream(imageBytes);
      var bufferedImage = read(inputStream);

      if (bufferedImage == null) {
        log.warn("Failed to read image, returning original");
        return new CompressedImageResult(imageBytes, originalContentType, originalSize, originalSize, 1.0);
      }

      // Java ImageIO doesn't natively support WebP, so we use optimized JPEG
      // WebP support would require additional library (e.g., imageio-webp)
      var compressedBytes = convertToOptimizedJpeg(bufferedImage);
      var contentType = "image/jpeg";

      var compressedSize = compressedBytes.length;
      var compressionRatio = (double) compressedSize / originalSize;

      return new CompressedImageResult(compressedBytes, contentType, originalSize, compressedSize, compressionRatio);
    } catch (Exception e) {
      log.error("Error compressing image: {}", e.getMessage(), e);
      return new CompressedImageResult(imageBytes, originalContentType, imageBytes.length, imageBytes.length, 1.0);
    }
  }

  private byte[] convertToOptimizedJpeg(BufferedImage image) throws IOException {
    var outputStream = new ByteArrayOutputStream();

    var imageWriters = getImageWritersByFormatName("jpeg");
    if (!imageWriters.hasNext()) {
      throw new IOException("No JPEG writer available");
    }

    var imageWriter = imageWriters.next();

    try (var imageOutputStream = createImageOutputStream(outputStream)) {
      imageWriter.setOutput(imageOutputStream);

      var writeParam = imageWriter.getDefaultWriteParam();
      if (writeParam.canWriteCompressed()) {
        writeParam.setCompressionMode(MODE_EXPLICIT);
        writeParam.setCompressionQuality(jpegQuality);
        writeParam.setCompressionType("JPEG");
      }

      var iioImage = removeMetadata ? new IIOImage(convertToRgb(image), null, null)
          : new IIOImage(image, null, null);
      imageWriter.write(null, iioImage, writeParam);
      imageWriter.dispose();

      return outputStream.toByteArray();
    }
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

  public String getFileExtensionForContentType(String contentType) {
    return switch (contentType.toLowerCase()) {
      case "image/webp" -> ".webp";
      case "image/png" -> ".png";
      default -> ".jpg";
    };
  }
}

