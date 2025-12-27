package com.truyengg.model.graphql;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Generic Relay-style connection for paginated results.
 */
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Connection<T> {

  List<Edge<T>> edges;
  PageInfo pageInfo;
  long totalCount;

  @Value
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class Edge<T> {
    T node;
    String cursor;
  }
}

