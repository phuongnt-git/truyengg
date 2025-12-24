package com.truyengg.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.ZonedDateTime;

import static java.time.ZonedDateTime.now;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean success,
    String message,
    T data,
    ZonedDateTime timestamp
) {
  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, null, data, now());
  }

  public static <T> ApiResponse<T> success(String message, T data) {
    return new ApiResponse<>(true, message, data, now());
  }

  public static <T> ApiResponse<T> error(String message) {
    return new ApiResponse<>(false, message, null, now());
  }

  public static <T> ApiResponse<T> error(String message, T data) {
    return new ApiResponse<>(false, message, data, now());
  }
}
