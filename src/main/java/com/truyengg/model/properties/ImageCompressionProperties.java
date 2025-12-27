package com.truyengg.model.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for image compression.
 */
@Configuration
@ConfigurationProperties(prefix = "image.compression")
@Getter
@Setter
public class ImageCompressionProperties {

  /**
   * Enable image compression during crawl.
   */
  private boolean enabled = true;

  /**
   * Default compression quality (0-100).
   */
  private int quality = 85;

  /**
   * Target format for compression.
   */
  private String format = "WEBP";

  /**
   * Maximum width for images (resize if larger).
   */
  private int maxWidth = 1200;

  /**
   * Minimum size in bytes to attempt compression.
   */
  private int minSizeBytes = 10000;
}

