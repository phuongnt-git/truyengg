package com.truyengg.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

@Component
@Slf4j
public class ImgBBUtil {

  private final String apiKey;
  private final WebClient webClient;

  public ImgBBUtil(@Value("${truyengg.imgbb.api-key:}") String apiKey) {
    this.apiKey = apiKey;
    this.webClient = WebClient.builder()
        .baseUrl("https://api.imgbb.com")
        .build();
  }

  public String uploadImage(String base64Image) {
    if (apiKey == null || apiKey.isEmpty()) {
      log.warn("ImgBB API key is not configured");
      return null;
    }

    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> response = (Map<String, Object>) webClient.post()
          .uri("/1/upload")
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(BodyInserters.fromFormData("key", apiKey)
              .with("image", base64Image))
          .retrieve()
          .bodyToMono(Map.class)
          .timeout(Duration.ofSeconds(30))
          .block();

      if (response != null && response.containsKey("data")) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        if (data.containsKey("url")) {
          return (String) data.get("url");
        }
      }
      log.error("Failed to upload image: {}", response);
      return null;
    } catch (Exception e) {
      log.error("Error uploading image to ImgBB", e);
      return null;
    }
  }
}
