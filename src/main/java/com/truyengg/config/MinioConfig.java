package com.truyengg.config;

import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MinioConfig {

  @Value("${truyengg.minio.endpoint:http://localhost:9000}")
  private String endpoint;

  @Value("${truyengg.minio.access-key:minioadmin}")
  private String accessKey;

  @Value("${truyengg.minio.secret-key:minioadmin}")
  private String secretKey;

  @Bean
  public MinioClient minioClient() {
    try {
      return MinioClient.builder()
          .endpoint(endpoint)
          .credentials(accessKey, secretKey)
          .build();
    } catch (Exception e) {
      log.error("Failed to initialize MinIO client: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to initialize MinIO client", e);
    }
  }
}

