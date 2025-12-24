package com.truyengg.service.crawl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.compressors.brotli.BrotliCompressorInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static com.truyengg.service.crawl.CrawlConstants.DATA_BUFFER_MAX_SIZE_BYTES;
import static com.truyengg.service.crawl.CrawlConstants.DEFAULT_DOMAIN;
import static com.truyengg.service.crawl.CrawlConstants.PROTOCOL_HTTP;
import static com.truyengg.service.crawl.CrawlConstants.PROTOCOL_HTTPS;
import static com.truyengg.service.crawl.CrawlConstants.USER_AGENTS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.ThreadLocalRandom.current;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.core.io.buffer.DataBufferUtils.release;
import static reactor.core.publisher.Mono.delay;

@Component
@RequiredArgsConstructor
@Slf4j
public class CrawlHttpClient {

  private final WebClient.Builder webClientBuilder;
  private final ObjectMapper objectMapper;

  @Value("${truyengg.crawl.max-retries:3}")
  private int maxRetries;

  @Value("${truyengg.crawl.retry-delay:2}")
  private int retryDelay;

  @Value("${truyengg.crawl.request-timeout:15}")
  private int requestTimeout;

  public List<String> buildHeaders(String domain) {
    return List.of(
        "Connection: keep-alive",
        "Cache-Control: max-age=0",
        "Upgrade-Insecure-Requests: 1",
        "User-Agent: " + getRandomUserAgent(),
        "Accept: text/html,application/json,application/xhtml+xml,application/xml;q=0.9,image/webp,image/jpeg,*/*;q=0.8",
        "Accept-Encoding: gzip, deflate, br",
        "Accept-Language: en-US,en;q=0.9,vi;q=0.8",
        "Referer: " + domain + "/"
    );
  }

  public String getRandomUserAgent() {
    return USER_AGENTS.get(current().nextInt(USER_AGENTS.size()));
  }

  public String fetchUrl(String url, List<String> headers, boolean isJson) {
    for (var attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        return executeRequest(url, headers, isJson);
      } catch (WebClientResponseException e) {
        if (!handleWebClientError(e, url, attempt)) {
          return EMPTY;
        }
      } catch (Exception e) {
        if (!handleGenericError(e, url, attempt)) {
          return EMPTY;
        }
      }
    }
    return EMPTY;
  }

  private String executeRequest(String url, List<String> headers, boolean isJson) {
    var webClient = webClientBuilder.build();
    var requestSpec = buildRequestSpec(webClient, url, headers, isJson);

    if (isJson) {
      var responseObj = requestSpec
          .retrieve()
          .bodyToMono(Object.class)
          .timeout(ofSeconds(requestTimeout))
          .block();

      if (responseObj instanceof Map<?, ?> response) {
        try {
          return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
          log.error("Failed to serialize JSON response from: {}", url, e);
          return EMPTY;
        }
      }
    }

    var responseEntity = requestSpec
        .retrieve()
        .toEntity(DataBuffer.class)
        .timeout(ofSeconds(requestTimeout))
        .block();

    if (isEmpty(responseEntity) || isEmpty(responseEntity.getBody())) {
      return EMPTY;
    }

    var dataBuffer = responseEntity.getBody();
    try {
      var responseBytes = readDataBuffer(dataBuffer);
      var contentEncoding = responseEntity.getHeaders().getFirst("Content-Encoding");
      var decompressedBytes = decompressResponse(responseBytes, contentEncoding);
      return new String(decompressedBytes, UTF_8);
    } finally {
      release(dataBuffer);
    }
  }

  private WebClient.RequestHeadersSpec<?> buildRequestSpec(WebClient webClient, String url,
                                                           List<String> headers, boolean isJson) {
    var requestSpec = webClient.get()
        .uri(url)
        .header("Connection", "keep-alive")
        .header("Cache-Control", "max-age=0")
        .header("Upgrade-Insecure-Requests", "1")
        .header("User-Agent", getRandomUserAgent())
        .header("Accept", isJson ? "application/json" : "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .header("Accept-Encoding", "gzip, deflate, br")
        .header("Accept-Language", "en-US,en;q=0.9,vi;q=0.8")
        .header("Sec-Fetch-Dest", "document")
        .header("Sec-Fetch-Mode", "navigate")
        .header("Sec-Fetch-Site", "none")
        .header("Sec-Fetch-User", "?1");

    for (var header : headers) {
      if (header.startsWith("Referer:")) {
        requestSpec.header("Referer", header.substring(9).trim());
        break;
      }
    }

    return requestSpec;
  }

  private byte[] readDataBuffer(DataBuffer dataBuffer) {
    var bytes = new byte[dataBuffer.readableByteCount()];
    dataBuffer.read(bytes);
    return bytes;
  }

  private byte[] decompressResponse(byte[] responseBytes, String contentEncoding) {
    if (isNotBlank(contentEncoding) && contentEncoding.contains("br")) {
      return decompressBrotli(responseBytes);
    }
    return responseBytes;
  }

  private boolean handleWebClientError(WebClientResponseException e, String url, int attempt) {
    var statusCode = e.getStatusCode().value();

    if (statusCode == 429) {
      if (attempt < maxRetries) {
        var delaySeconds = calculateExponentialDelay(attempt) * 2; // Double delay for 429
        log.warn("Rate limit (429) hit for URL: {}, retrying after {} seconds (attempt {}/{})",
            url, delaySeconds, attempt, maxRetries);
        waitBeforeRetry(delaySeconds);
        return true;
      }
      log.error("Rate limit exceeded after {} attempts for URL: {}", maxRetries, url);
      return false;
    }

    if (statusCode == 403 || statusCode == 503) {
      if (attempt < maxRetries) {
        waitBeforeRetry(calculateExponentialDelay(attempt));
        return true;
      }
      log.error("Blocked after {} attempts for URL: {}", maxRetries, url);
      return false;
    }

    log.error("HTTP Error {} for URL: {}", statusCode, url);
    return false;
  }

  private boolean handleGenericError(Exception e, String url, int attempt) {
    log.error("Error fetching URL: {}", url, e);
    if (attempt < maxRetries) {
      waitBeforeRetry(retryDelay);
      return true;
    }
    return false;
  }

  private int calculateExponentialDelay(int attempt) {
    return retryDelay * (int) Math.pow(2, attempt - 1.0);
  }

  private void waitBeforeRetry(int delaySeconds) {
    delay(ofSeconds(delaySeconds)).block();
  }

  public byte[] downloadImage(String imageUrl, List<String> headers) {
    for (var attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        var webClient = webClientBuilder.build();

        var requestSpec = webClient.get()
            .uri(imageUrl)
            .header("User-Agent", getRandomUserAgent())
            .header("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")
            .header("Referer", extractReferer(headers));

        var dataBufferFlux = requestSpec
            .retrieve()
            .bodyToFlux(DataBuffer.class)
            .timeout(ofSeconds(requestTimeout));

        var dataBuffer = DataBufferUtils.join(dataBufferFlux, DATA_BUFFER_MAX_SIZE_BYTES)
            .block();

        if (isNotEmpty(dataBuffer)) {
          try {
            var imageBytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(imageBytes);
            return imageBytes;
          } finally {
            release(dataBuffer);
          }
        }
      } catch (WebClientResponseException e) {
        var statusCode = e.getStatusCode().value();
        if (statusCode == 429) {
          if (attempt < maxRetries) {
            var delaySeconds = calculateExponentialDelay(attempt) * 2;
            log.warn("Rate limit (429) when downloading image: {}, retrying after {} seconds (attempt {}/{})",
                imageUrl, delaySeconds, attempt, maxRetries);
            waitBeforeRetry(delaySeconds);
            continue;
          }
          log.error("Rate limit exceeded after {} attempts for image: {}", maxRetries, imageUrl);
          return new byte[0];
        }
        if ((statusCode == 403 || statusCode == 503) && attempt < maxRetries) {
          waitBeforeRetry(calculateExponentialDelay(attempt));
          continue;
        }

        log.error("HTTP Error {} downloading image: {}", statusCode, imageUrl);
        return new byte[0];
      } catch (Exception e) {
        if (attempt < maxRetries) {
          log.warn("Error downloading image: {}, retrying (attempt {}/{})", imageUrl, attempt, maxRetries);
          waitBeforeRetry(retryDelay);
          continue;
        }
        log.error("Error downloading image: {}", imageUrl, e);
        return new byte[0];
      }
    }
    return new byte[0];
  }

  private String extractReferer(List<String> headers) {
    for (var header : headers) {
      if (header.startsWith("Referer:")) {
        return header.substring(9).trim();
      }
    }
    return EMPTY;
  }

  private byte[] decompressBrotli(byte[] compressedData) {
    try (var inputStream = new BrotliCompressorInputStream(new ByteArrayInputStream(compressedData));
         var outputStream = new ByteArrayOutputStream()) {

      var buffer = new byte[8192];
      int bytesRead;
      while ((bytesRead = inputStream.read(buffer)) != -1) {
        outputStream.write(buffer, 0, bytesRead);
      }

      return outputStream.toByteArray();
    } catch (Exception e) {
      log.error("Error decompressing Brotli data", e);
      return new byte[0];
    }
  }

  public String extractDomainFromUrl(String url) {
    try {
      if (url.startsWith(PROTOCOL_HTTP) || url.startsWith(PROTOCOL_HTTPS)) {
        var start = url.indexOf("://") + 3;
        var end = url.indexOf("/", start);
        if (end == -1) {
          end = url.length();
        }
        var domain = url.substring(0, end);
        if (!domain.startsWith(PROTOCOL_HTTP) && !domain.startsWith(PROTOCOL_HTTPS)) {
          domain = PROTOCOL_HTTPS + domain;
        }
        return domain;
      }
    } catch (Exception e) {
      // Fallback to default domain
    }
    return DEFAULT_DOMAIN;
  }

  public String normalizeUrl(String url, String domain) {
    if (isNotBlank(url)) {
      return url;
    }

    if (url.startsWith(PROTOCOL_HTTP) || url.startsWith(PROTOCOL_HTTPS)) {
      return url;
    }

    if (url.startsWith("/")) {
      return domain + url;
    }

    var normalizedDomain = domain.endsWith("/") ? domain.substring(0, domain.length() - 1) : domain;
    return normalizedDomain + "/" + url;
  }
}

