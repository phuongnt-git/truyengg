package com.truyengg.security.qsc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.truyengg.service.config.QSCSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class HPKEWebSocketInterceptor implements ChannelInterceptor {

  private final HPKEService hpkeService;
  private final QSCSettingsService qscSettings;
  private final ObjectMapper objectMapper;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    var destination = (String) message.getHeaders().get("simpDestination");

    if (destination != null && shouldEncrypt(destination, message)) {
      try {
        var jsonBytes = objectMapper.writeValueAsBytes(message.getPayload());

        if (jsonBytes.length < qscSettings.getWebSocketThreshold()) {
          return message;
        }

        var encrypted = hpkeService.encrypt(jsonBytes);

        return MessageBuilder.withPayload(encrypted)
            .copyHeaders(message.getHeaders())
            .setHeader("encrypted", "kyber-hpke")
            .build();

      } catch (Exception e) {
        log.error("[QSC] WebSocket encryption failed", e);
      }
    }
    return message;
  }

  private boolean shouldEncrypt(String destination, Message<?> message) {
    return qscSettings.isWebSocketEncryptionEnabled() &&
        (destination.startsWith("/topic/crawl-progress/") ||
            destination.startsWith("/topic/category-crawl-progress/"));
  }
}

