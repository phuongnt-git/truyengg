package com.truyengg.model.graphql;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

import java.time.ZonedDateTime;

/**
 * Message DTO for GraphQL message type.
 */
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MessageDto {

  String id;
  ZonedDateTime timestamp;
  MessageFilter.MessageLevel level;
  String message;

  /**
   * Parse a message string to extract timestamp, level, and content.
   * Expected format: "[TIMESTAMP] [LEVEL] message content"
   */
  public static MessageDto fromString(String message, int index) {
    // Default values
    var timestamp = ZonedDateTime.now();
    var level = MessageFilter.MessageLevel.INFO;

    // Try to parse timestamp from beginning if present
    if (message != null && !message.isEmpty()) {
      // Parse level from message
      if (message.contains("[ERROR]") || message.toLowerCase().contains("error")
          || message.toLowerCase().contains("failed")) {
        level = MessageFilter.MessageLevel.ERROR;
      } else if (message.contains("[WARN]") || message.toLowerCase().contains("warn")) {
        level = MessageFilter.MessageLevel.WARN;
      }
    }

    return MessageDto.builder()
        .id(String.valueOf(index))
        .timestamp(timestamp)
        .level(level)
        .message(message != null ? message : "")
        .build();
  }
}

