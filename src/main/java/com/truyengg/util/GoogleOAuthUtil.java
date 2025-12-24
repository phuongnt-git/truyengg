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
public class GoogleOAuthUtil {

  private final String clientId;
  private final String clientSecret;
  private final WebClient webClient;

  public GoogleOAuthUtil(
      @Value("${truyengg.oauth2.google.client-id:}") String clientId,
      @Value("${truyengg.oauth2.google.client-secret:}") String clientSecret,
      WebClient.Builder webClientBuilder) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.webClient = webClientBuilder
        .baseUrl("https://oauth2.googleapis.com")
        .build();
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> exchangeCodeForToken(String code, String redirectUri) {
    try {
      return (Map<String, Object>) webClient.post()
          .uri("/token")
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(BodyInserters.fromFormData("code", code)
              .with("client_id", clientId)
              .with("client_secret", clientSecret)
              .with("redirect_uri", redirectUri)
              .with("grant_type", "authorization_code"))
          .retrieve()
          .bodyToMono(Map.class)
          .timeout(Duration.ofSeconds(10))
          .block();
    } catch (Exception e) {
      log.error("Error exchanging code for token", e);
      throw new RuntimeException("Không thể lấy token từ Google", e);
    }
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getUserInfo(String accessToken) {
    try {
      return (Map<String, Object>) webClient.get()
          .uri("https://www.googleapis.com/oauth2/v2/userinfo")
          .header("Authorization", "Bearer " + accessToken)
          .retrieve()
          .bodyToMono(Map.class)
          .timeout(Duration.ofSeconds(10))
          .block();
    } catch (Exception e) {
      log.error("Error getting user info from Google", e);
      throw new RuntimeException("Không thể lấy thông tin user từ Google", e);
    }
  }
}
