package com.truyengg.model.graphql;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * GraphQL input type for filtering messages.
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MessageFilter {

  List<MessageLevel> levels;
  String search;
  ZonedDateTime timestampAfter;
  ZonedDateTime timestampBefore;

  public enum MessageLevel {
    INFO,
    WARN,
    ERROR
  }
}

