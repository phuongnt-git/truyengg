package com.truyengg.service.auth;

import com.truyengg.domain.enums.UserRole;
import com.truyengg.domain.repository.UserRepository;
import com.truyengg.domain.exception.ResourceNotFoundException;
import com.truyengg.domain.exception.ValidationException;
import com.truyengg.model.response.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

import static org.springframework.util.StringUtils.hasText;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

  private static final String USER_NOT_FOUND = "User not found";
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Transactional(readOnly = true)
  public Page<UserResponse> getAllUsers(Pageable pageable) {
    return userRepository.findAll(pageable)
        .map(UserResponse::from);
  }

  @Transactional(readOnly = true)
  public UserResponse getUserById(Long id) {
    var user = userRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
    return UserResponse.from(user);
  }

  /**
   * Get User entity by ID for internal service use.
   */
  @Transactional(readOnly = true)
  public com.truyengg.domain.entity.User getUserEntityById(Long id) {
    return userRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
  }

  @Transactional
  public UserResponse updateUser(Long id, String username, String firstName, String lastName, String avatar) {
    var user = userRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));

    if (hasText(username)) {
      user.setUsername(username);
    }
    if (hasText(firstName)) {
      user.setFirstName(firstName);
    }
    if (hasText(lastName)) {
      user.setLastName(lastName);
    }
    if (hasText(avatar)) {
      user.setAvatar(avatar);
    }

    user = userRepository.save(user);
    return UserResponse.from(user);
  }

  @Transactional
  public void updateUserRole(Long id, UserRole role) {
    var user = userRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
    user.setRoles(role);
    userRepository.save(user);
  }

  @Transactional
  public void banUser(Long id, ZonedDateTime bannedUntil) {
    var user = userRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
    user.setBannedUntil(bannedUntil);
    userRepository.save(user);
  }

  @Transactional
  public void unbanUser(Long id) {
    var user = userRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
    user.setBannedUntil(null);
    userRepository.save(user);
  }

  @Transactional
  public void deleteUser(Long id) {
    if (!userRepository.existsById(id)) {
      throw new ResourceNotFoundException(USER_NOT_FOUND);
    }
    userRepository.deleteById(id);
  }

  @Transactional(readOnly = true)
  public UserResponse getCurrentUserProfile(Long userId) {
    var user = userRepository.findById(userId)
        .orElseThrow(() -> new AuthenticationServiceException(USER_NOT_FOUND));
    return UserResponse.from(user);
  }

  @Transactional
  public void updateAvatar(Long userId, String avatarUrl) {
    var user = userRepository.findById(userId)
        .orElseThrow(() -> new AuthenticationServiceException(USER_NOT_FOUND));
    user.setAvatar(avatarUrl);
    userRepository.save(user);
  }

  @Transactional
  public void changePassword(Long userId, String oldPassword, String newPassword) {
    var user = userRepository.findById(userId)
        .orElseThrow(() -> new AuthenticationServiceException(USER_NOT_FOUND));

    if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
      throw new ValidationException("Mật khẩu cũ không đúng");
    }

    user.setPassword(passwordEncoder.encode(newPassword));
    userRepository.save(user);
  }
}
