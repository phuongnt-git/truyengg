package com.truyengg.config;

import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO configuration - only enabled when storage.type=minio.
 * When storage.type=local, this configuration is not loaded and app can run without MinIO.
 */
@Configuration
@ConditionalOnProperty(name = "truyengg.storage.type", havingValue = "minio", matchIfMissing = true)
@Slf4j
public class MinioConfig {

  @Value("${truyengg.storage.minio.endpoint:http://localhost:9000}")
  private String endpoint;

  @Value("${truyengg.storage.minio.access-key:minioadmin}")
  private String accessKey;

  @Value("${truyengg.storage.minio.secret-key:minioadmin}")
  private String secretKey;

  @Bean
  public MinioClient minioClient() {
    try {
      return MinioClient.builder()
          .endpoint(endpoint)
          .credentials(accessKey, secretKey)
          .build();
    } catch (Exception e) {
      log.warn("Failed to initialize MinIO client: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to initialize MinIO client", e);
    }
  }
}

