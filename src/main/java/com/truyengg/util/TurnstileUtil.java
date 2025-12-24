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
public class TurnstileUtil {

  private final String secretKey;
  private final WebClient webClient;

  public TurnstileUtil(@Value("${truyengg.turnstile.secret-key:}") String secretKey) {
    this.secretKey = secretKey;
    this.webClient = WebClient.builder()
        .baseUrl("https://challenges.cloudflare.com")
        .build();
  }

  public boolean verify(String token, String remoteIp) {
    if (secretKey == null || secretKey.isEmpty()) {
      log.warn("Turnstile secret key is not configured");
      return false;
    }

    if (token == null || token.isEmpty()) {
      return false;
    }

    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> response = (Map<String, Object>) webClient.post()
          .uri("/turnstile/v0/siteverify")
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(BodyInserters.fromFormData("secret", secretKey)
              .with("response", token)
              .with("remoteip", remoteIp != null ? remoteIp : ""))
          .retrieve()
          .bodyToMono(Map.class)
          .timeout(Duration.ofSeconds(10))
          .block();

      if (response != null && Boolean.TRUE.equals(response.get("success"))) {
        return true;
      }
      log.warn("Turnstile verification failed: {}", response);
      return false;
    } catch (Exception e) {
      log.error("Error verifying Turnstile token", e);
      return false;
    }
  }
}
