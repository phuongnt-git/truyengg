package com.truyengg.controller.api;

import com.truyengg.model.request.LoginRequest;
import com.truyengg.model.request.RegisterRequest;
import com.truyengg.model.response.ApiResponse;
import com.truyengg.model.response.TokenResponse;
import com.truyengg.model.response.UserResponse;
import com.truyengg.security.UserPrincipal;
import com.truyengg.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.springframework.http.HttpHeaders.SET_COOKIE;
import static org.springframework.http.ResponseCookie.from;

@Tag(name = "Authentication", description = "APIs for user authentication and authorization")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private static final String ACCESS_TOKEN = "access_token";
  private static final String REFRESH_TOKEN = "refresh_token";
  private final AuthService authService;
  @Value("${truyengg.jwt.refresh-token-cookie-max-age}")
  private int refreshTokenMaxAge;

  @PostMapping("/login")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<TokenResponse>> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest) {
    var userAgent = httpRequest.getHeader("User-Agent");
    var tokenResponse = authService.login(request, httpRequest.getRemoteAddr(), userAgent);

    return buildTokenResponse(tokenResponse, httpRequest, "Đăng nhập thành công");
  }

  @PostMapping("/register")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<UserResponse>> register(
      @Valid @RequestBody RegisterRequest request,
      HttpServletRequest httpRequest) {
    var user = authService.register(request, httpRequest.getRemoteAddr());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("Đăng ký thành công", user));
  }

  @PostMapping("/logout")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Object>> logout(
      @RequestHeader(value = "Authorization", required = false) String token,
      HttpServletRequest httpRequest) {
    var accessToken = extractToken(token, httpRequest, ACCESS_TOKEN);
    var refreshToken = extractTokenFromCookie(httpRequest, REFRESH_TOKEN);

    authService.logout(accessToken, refreshToken);

    return ResponseEntity.ok()
        .header(SET_COOKIE, createExpiredCookie(ACCESS_TOKEN).toString())
        .header(SET_COOKIE, createExpiredCookie(REFRESH_TOKEN).toString())
        .body(ApiResponse.success("Đăng xuất thành công"));
  }

  @PostMapping("/refresh")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(
      @RequestHeader(value = "Authorization", required = false) String token,
      HttpServletRequest httpRequest) {
    var refreshToken = extractToken(token, httpRequest, REFRESH_TOKEN);

    if (isEmpty(refreshToken)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(ApiResponse.error("Refresh token không được để trống"));
    }

    var userAgent = httpRequest.getHeader("User-Agent");
    var tokenResponse = authService.refreshToken(refreshToken, httpRequest.getRemoteAddr(), userAgent);

    return buildTokenResponse(tokenResponse, httpRequest, "Token đã được làm mới");
  }

  @GetMapping("/me")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
      @AuthenticationPrincipal UserPrincipal userPrincipal) {
    var user = authService.getCurrentUser(userPrincipal.getId());
    return ResponseEntity.ok(ApiResponse.success(user));
  }

  @PostMapping("/forgot-password")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Object>> forgotPassword(@RequestParam String email) {
    authService.requestPasswordReset(email);
    return ResponseEntity.ok(ApiResponse.success("Email đặt lại mật khẩu đã được gửi"));
  }

  @PostMapping("/reset-password")
  @Operation(summary = "Endpoint", description = "API endpoint")
  public ResponseEntity<ApiResponse<Object>> resetPassword(
      @RequestParam String token,
      @RequestParam String newPassword) {
    authService.resetPassword(token, newPassword);
    return ResponseEntity.ok(ApiResponse.success("Mật khẩu đã được đặt lại thành công"));
  }

  private String extractToken(String authHeader, HttpServletRequest request, String cookieName) {
    if (isNotEmpty(authHeader)) {
      return authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
    }
    return extractTokenFromCookie(request, cookieName);
  }

  private String extractTokenFromCookie(HttpServletRequest request, String cookieName) {
    var cookies = request.getCookies();
    if (cookies == null) return EMPTY;

    for (var cookie : cookies) {
      if (cookieName.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return EMPTY;
  }

  private ResponseCookie createExpiredCookie(String name) {
    return from(name, EMPTY)
        .httpOnly(true)
        .path("/")
        .maxAge(0)
        .build();
  }

  private ResponseEntity<ApiResponse<TokenResponse>> buildTokenResponse(
      TokenResponse tokenResponse, HttpServletRequest request, String message) {
    var accessTokenCookie = from(ACCESS_TOKEN, tokenResponse.accessToken())
        .httpOnly(true)
        .secure(request.isSecure())
        .path("/")
        .maxAge(tokenResponse.expiresIn().intValue())
        .sameSite("Lax")
        .build();

    var refreshTokenCookie = from(REFRESH_TOKEN, tokenResponse.refreshToken())
        .httpOnly(true)
        .secure(request.isSecure())
        .path("/")
        .maxAge(refreshTokenMaxAge)
        .sameSite("Lax")
        .build();

    return ResponseEntity.ok()
        .header(SET_COOKIE, accessTokenCookie.toString())
        .header(SET_COOKIE, refreshTokenCookie.toString())
        .body(ApiResponse.success(message, tokenResponse));
  }
}

