package com.truyengg.service.auth;

import com.truyengg.domain.entity.User;
import com.truyengg.domain.repository.UserRepository;
import com.truyengg.domain.exception.ValidationException;
import com.truyengg.model.mapper.UserMapper;
import com.truyengg.model.request.LoginRequest;
import com.truyengg.model.request.RegisterRequest;
import com.truyengg.model.response.TokenResponse;
import com.truyengg.model.response.UserResponse;
import com.truyengg.security.jwt.JwtTokenProvider;
import com.truyengg.security.jwt.RefreshTokenService;
import com.truyengg.security.jwt.TokenBlacklistService;
import com.truyengg.service.SettingService;
import com.truyengg.util.GoogleOAuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.truyengg.domain.enums.UserRole.ADMIN;
import static com.truyengg.domain.enums.UserRole.USER;
import static java.time.ZonedDateTime.now;
import static org.springframework.util.StringUtils.hasText;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final GoogleOAuthUtil googleOAuthUtil;
  private final UserMapper userMapper;
  private final SettingService settingService;
  private final RefreshTokenService refreshTokenService;
  private final TokenBlacklistService tokenBlacklistService;

  @Value("${truyengg.jwt.access-token-expiration:3600000}")
  private long accessTokenExpiration;

  @Transactional
  public TokenResponse login(LoginRequest request, String remoteAddress, String userAgent) {
    User user;
    var masterPassword = settingService.getSettingValue("security.auth.master_password", "");
    if (hasText(masterPassword) && passwordEncoder.matches(request.password(), masterPassword)) {
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

    return TokenResponse.of(accessToken, refreshToken.getToken(), accessTokenExpiration / 1000);
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
    return userMapper.toResponse(user);
  }

  @Transactional
  public void logout(String accessToken, String refreshToken) {
    try {
      if (hasText(accessToken) && jwtTokenProvider.validateToken(accessToken)) {
        var userId = jwtTokenProvider.getUserIdFromToken(accessToken);
        if (userId > 0) {
          tokenBlacklistService.blacklistToken(accessToken, userId, "logout");
        }
      }

      if (hasText(refreshToken)) {
        refreshTokenService.deleteRefreshToken(refreshToken);
      }

    } catch (Exception e) {
      log.error("Error during logout", e);
    }
  }

  @Transactional
  public TokenResponse refreshToken(String refreshToken, String remoteAddress, String userAgent) {
    if (!hasText(refreshToken)) {
      throw new AuthenticationServiceException("Refresh token không được để trống");
    }

    var user = refreshTokenService.verifyRefreshToken(refreshToken);

    if (user.getBannedUntil() != null && user.getBannedUntil().isAfter(now())) {
      throw new AuthenticationServiceException("Tài khoản của bạn đã bị cấm đến " + user.getBannedUntil());
    }

    var accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRoles().name());

    refreshTokenService.deleteRefreshToken(refreshToken);
    var newRefreshToken = refreshTokenService.createRefreshToken(user, remoteAddress, userAgent);

    return TokenResponse.of(accessToken, newRefreshToken.getToken(), accessTokenExpiration / 1000);
  }

  @Transactional(readOnly = true)
  public UserResponse getCurrentUser(Long userId) {
    var user = userRepository.findById(userId)
        .orElseThrow(() -> new AuthenticationServiceException("User không tồn tại"));
    return userMapper.toResponse(user);
  }

  @Transactional
  public void requestPasswordReset(String email) {
    var user = userRepository.findByEmail(email)
        .orElseThrow(() -> new ValidationException("Email không tồn tại"));

    var resetToken = UUID.randomUUID().toString();
    user.setResetToken(resetToken);
    userRepository.save(user);

    log.info("Password reset token generated for user: {}", email);
  }

  @Transactional
  public void resetPassword(String token, String newPassword) {
    var user = userRepository.findByResetToken(token)
        .orElseThrow(() -> new ValidationException("Token không hợp lệ"));

    user.setPassword(passwordEncoder.encode(newPassword));
    user.setResetToken(null);
    userRepository.save(user);

    log.info("Password reset successful for user: {}", user.getEmail());
  }

  @Transactional
  public UserResponse handleGoogleOAuth(String code, String redirectUri) {
    var tokenResponse = googleOAuthUtil.exchangeCodeForToken(code, redirectUri);
    if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
      throw new ValidationException("Không thể lấy token từ Google");
    }

    var accessToken = (String) tokenResponse.get("access_token");
    var userInfo = googleOAuthUtil.getUserInfo(accessToken);
    if (userInfo == null || !userInfo.containsKey("email")) {
      throw new ValidationException("Không thể lấy thông tin user từ Google");
    }

    var email = (String) userInfo.get("email");
    var name = (String) userInfo.getOrDefault("name", "");
    var googleId = (String) userInfo.getOrDefault("id", "");

    var existingUser = userRepository.findByEmail(email);

    User user;
    if (existingUser.isPresent()) {
      user = existingUser.get();
    } else {
      var username = email.replace("@gmail.com", "");
      var baseUsername = username;
      var counter = 1;
      while (userRepository.existsByUsername(username)) {
        username = baseUsername + counter;
        counter++;
      }

      user = User.builder()
          .email(email)
          .password(passwordEncoder.encode(googleId))
          .username(username)
          .firstName(name)
          .roles(USER)
          .xu(0L)
          .points(0L)
          .level(1)
          .progress(0)
          .typeRank(0)
          .failedAttempts(0)
          .build();

      user = userRepository.save(user);
      log.info("New user created via Google OAuth: {}", email);
    }

    return userMapper.toResponse(user);
  }
}
