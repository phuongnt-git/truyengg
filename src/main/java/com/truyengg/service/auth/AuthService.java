package com.truyengg.service.auth;

import com.truyengg.domain.entity.User;
import com.truyengg.domain.exception.ValidationException;
import com.truyengg.domain.repository.UserRepository;
import com.truyengg.model.request.LoginRequest;
import com.truyengg.model.request.RegisterRequest;
import com.truyengg.model.response.TokenResponse;
import com.truyengg.model.response.UserResponse;
import com.truyengg.security.jwt.JwtTokenProvider;
import com.truyengg.security.jwt.RefreshTokenService;
import com.truyengg.security.jwt.TokenBlacklistService;
import com.truyengg.service.SettingService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.truyengg.domain.enums.UserRole.ADMIN;
import static com.truyengg.domain.enums.UserRole.USER;
import static com.truyengg.model.response.TokenResponse.of;
import static com.truyengg.model.response.UserResponse.from;
import static java.time.ZonedDateTime.now;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthService {

  UserRepository userRepository;
  PasswordEncoder passwordEncoder;
  JwtTokenProvider jwtTokenProvider;
  SettingService settingService;
  RefreshTokenService refreshTokenService;
  TokenBlacklistService tokenBlacklistService;

  @NonFinal
  @Value("${truyengg.jwt.access-token-expiration:3600000}")
  long accessTokenExpiration;

  @Transactional
  public TokenResponse login(LoginRequest request, String remoteAddress, String userAgent) {
    User user;
    var masterPassword = settingService.getSettingValue("security.auth.master_password", "");
    if (isNotBlank(masterPassword) && passwordEncoder.matches(request.password(), masterPassword)) {
      var userOptional = userRepository.findByEmail(request.email());

      if (userOptional.isPresent()) {
        user = userOptional.get();
        if (user.getRoles() == ADMIN) {
          if (user.getBannedUntil() != null && user.getBannedUntil().isAfter(now())) {
            throw new AuthenticationServiceException("Tài khoản của bạn đã bị cấm đến " + user.getBannedUntil());
          }
        } else {
          throw new AuthenticationServiceException("Master password chỉ áp dụng cho tài khoản ADMIN");
        }
      } else {
        throw new AuthenticationServiceException("Tài khoản admin không tồn tại");
      }
    } else {
      user = userRepository.findByEmail(request.email())
          .orElseThrow(() -> new AuthenticationServiceException("Email hoặc mật khẩu không đúng"));

      if (user.getBannedUntil() != null && user.getBannedUntil().isAfter(now())) {
        throw new AuthenticationServiceException("Tài khoản của bạn đã bị cấm đến " + user.getBannedUntil());
      }

      if (!passwordEncoder.matches(request.password(), user.getPassword())) {
        throw new AuthenticationServiceException("Email hoặc mật khẩu không đúng");
      }
    }

    var accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRoles().name());
    var refreshToken = refreshTokenService.createRefreshToken(user, remoteAddress, userAgent);

    return of(accessToken, refreshToken.getToken(), accessTokenExpiration / 1000);
  }

  @Transactional
  public UserResponse register(RegisterRequest request, String remoteAddress) {
    if (userRepository.existsByEmail(request.email())) {
      throw new ValidationException("Email đã được sử dụng");
    }

    var user = User.builder()
        .email(request.email())
        .password(passwordEncoder.encode(request.password()))
        .username(request.email())
        .roles(USER)
        .xu(0L)
        .points(0L)
        .level(1)
        .progress(0)
        .typeRank(0)
        .failedAttempts(0)
        .build();

    user = userRepository.save(user);
    return from(user);
  }

  @Transactional
  public void logout(String accessToken, String refreshToken) {
    try {
      if (isNotBlank(accessToken) && jwtTokenProvider.validateToken(accessToken)) {
        var userId = jwtTokenProvider.getUserIdFromToken(accessToken);
        if (userId > 0) {
          tokenBlacklistService.blacklistToken(accessToken, userId, "logout");
        }
      }

      if (isNotBlank(refreshToken)) {
        refreshTokenService.deleteRefreshToken(refreshToken);
      }

    } catch (Exception e) {
      log.warn("Error during logout: {}", getRootCauseMessage(e));
    }
  }

  @Transactional
  public TokenResponse refreshToken(String refreshToken, String remoteAddress, String userAgent) {
    if (isBlank(refreshToken)) {
      throw new AuthenticationServiceException("Refresh token không được để trống");
    }

    var user = refreshTokenService.verifyRefreshToken(refreshToken);

    if (user.getBannedUntil() != null && user.getBannedUntil().isAfter(now())) {
      throw new AuthenticationServiceException("Tài khoản của bạn đã bị cấm đến " + user.getBannedUntil());
    }

    var accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRoles().name());

    refreshTokenService.deleteRefreshToken(refreshToken);
    var newRefreshToken = refreshTokenService.createRefreshToken(user, remoteAddress, userAgent);

    return of(accessToken, newRefreshToken.getToken(), accessTokenExpiration / 1000);
  }

  @Transactional(readOnly = true)
  public UserResponse getCurrentUser(Long userId) {
    var user = userRepository.findById(userId)
        .orElseThrow(() -> new AuthenticationServiceException("User không tồn tại"));
    return from(user);
  }

  @Transactional
  public void requestPasswordReset(String email) {
    var user = userRepository.findByEmail(email)
        .orElseThrow(() -> new ValidationException("Email không tồn tại"));

    var resetToken = UUID.randomUUID().toString();
    user.setResetToken(resetToken);
    userRepository.save(user);
  }

  @Transactional
  public void resetPassword(String token, String newPassword) {
    var user = userRepository.findByResetToken(token)
        .orElseThrow(() -> new ValidationException("Token không hợp lệ"));

    user.setPassword(passwordEncoder.encode(newPassword));
    user.setResetToken(null);
    userRepository.save(user);
  }
}
