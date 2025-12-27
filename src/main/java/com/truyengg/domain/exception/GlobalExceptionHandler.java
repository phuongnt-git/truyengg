package com.truyengg.domain.exception;

import com.truyengg.model.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

import static com.truyengg.model.response.ApiResponse.error;
import static org.apache.commons.lang3.exception.ExceptionUtils.getMessage;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.ResponseEntity.status;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler({ResourceNotFoundException.class, NoResourceFoundException.class})
  public ResponseEntity<ApiResponse<Object>> handleResourceNotFoundException(Exception ex,
                                                                             HttpServletRequest request) {
    log.warn("Resource not found: {}", getMessage(ex));
    return status(NOT_FOUND)
        .body(error(getMessage(ex)));
  }

  @ExceptionHandler(AuthenticationServiceException.class)
  public ResponseEntity<ApiResponse<Object>> handleUnauthorizedException(AuthenticationServiceException ex) {
    log.warn("Unauthorized: {}", getMessage(ex));
    return status(UNAUTHORIZED)
        .body(error(getMessage(ex)));
  }

  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ApiResponse<Object>> handleValidationException(ValidationException ex) {
    log.warn("Validation error: {}", getMessage(ex));
    return status(BAD_REQUEST)
        .body(error(getMessage(ex)));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
    var errors = new HashMap<String, String>();
    ex.getBindingResult().getAllErrors().forEach(error -> {
      var fieldName = ((FieldError) error).getField();
      var errorMessage = error.getDefaultMessage();
      errors.put(fieldName, errorMessage);
    });
    log.warn("Validation errors: {}", errors);
    return status(BAD_REQUEST)
        .body(error("Validation failed", errors));
  }

  @ExceptionHandler(HttpMessageNotWritableException.class)
  public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotWritableException(
      HttpMessageNotWritableException ex, HttpServletRequest request) {
    var requestPath = request.getRequestURI();

    if (requestPath != null && (requestPath.startsWith("/api/images/proxy")
        || requestPath.startsWith("/api/images/original-proxy"))) {
      log.warn("HttpMessageNotWritableException on image proxy endpoint {}: {}",
          requestPath, getMessage(ex));
      return status(INTERNAL_SERVER_ERROR).build();
    }

    log.error("HttpMessageNotWritableException: {}", getMessage(ex));
    return status(INTERNAL_SERVER_ERROR)
        .body(error("Failed to write response"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex) {
    log.warn("Exception errors: {}", getMessage(ex));
    return status(INTERNAL_SERVER_ERROR)
        .body(error("An unexpected error occurred"));
  }
}

