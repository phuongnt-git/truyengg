package com.truyengg.model.graphql;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

/**
 * GraphQL input type for retrying specific failed images.
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RetryImagesInput {

  UUID jobId;
  List<Integer> indices;
}

