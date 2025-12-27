package com.truyengg.security.passkey;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.truyengg.domain.entity.UserPasskey;
import com.truyengg.domain.exception.ResourceNotFoundException;
import com.truyengg.domain.repository.UserPasskeyRepository;
import com.truyengg.domain.repository.UserRepository;
import com.truyengg.model.response.TokenResponse;
import com.truyengg.security.jwt.JwtTokenProvider;
import com.truyengg.security.jwt.RefreshTokenService;
import com.truyengg.security.passkey.model.AuthenticationStartResponse;
import com.truyengg.security.passkey.model.PasskeyFinishAuthenticationRequest;
import com.truyengg.security.passkey.model.PasskeyFinishRegistrationRequest;
import com.truyengg.security.passkey.model.PasskeyResponse;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.AuthenticatorTransport;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.truyengg.domain.constant.AppConstants.AUTHENTICATION_CACHE;
import static com.truyengg.domain.constant.AppConstants.REGISTRATION_CACHE;
import static com.truyengg.domain.enums.UserRole.ADMIN;
import static com.truyengg.model.response.TokenResponse.of;
import static com.yubico.webauthn.data.PublicKeyCredential.parseRegistrationResponseJson;
import static java.time.ZonedDateTime.now;
import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PasskeyService {

  RelyingParty relyingParty;
  UserPasskeyRepository userPasskeyRepository;
  UserRepository userRepository;
  JwtTokenProvider jwtTokenProvider;
  RefreshTokenService refreshTokenService;
  CacheManager cacheManager;
  ObjectMapper objectMapper;

  @NonFinal
  @Value("${truyengg.jwt.access-token-expiration:3600000}")
  long accessTokenExpiration;

  @Transactional(readOnly = true)
  public String startRegistration(Long userId, String deviceName) {
    var user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    if (user.getRoles() != ADMIN) {
      throw new IllegalStateException("Only admin users can register passkeys");
    }

    if (userPasskeyRepository.existsByUserIdAndDeviceName(userId, deviceName)) {
      throw new IllegalStateException("A passkey with this device name already exists");
    }

    var userIdentity = UserIdentity.builder()
        .name(user.getEmail())
        .displayName(defaultIfBlank(user.getUsername(), user.getEmail()))
        .id(new ByteArray(longToBytes(userId)))
        .build();

    var authenticatorSelection = AuthenticatorSelectionCriteria.builder()
        .residentKey(ResidentKeyRequirement.PREFERRED)
        .userVerification(UserVerificationRequirement.PREFERRED)
        .build();

    var startOptions = StartRegistrationOptions.builder()
        .user(userIdentity)
        .authenticatorSelection(authenticatorSelection)
        .timeout(300_000L) // 5 minutes
        .build();

    var options = relyingParty.startRegistration(startOptions);

    // Store options in cache for later verification
    var registrationCache = getRegistrationCache();
    var cacheKey = userId + ":" + deviceName;
    registrationCache.put(cacheKey, options);

    try {
      return options.toCredentialsCreateJson();
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize registration options");
    }
  }

  @Transactional
  public PasskeyResponse finishRegistration(Long userId, PasskeyFinishRegistrationRequest request) {
    var registrationCache = getRegistrationCache();
    var cacheKey = userId + ":" + request.deviceName();
    var cachedOptions = registrationCache.get(cacheKey, PublicKeyCredentialCreationOptions.class);

    if (cachedOptions == null) {
      throw new IllegalStateException("Registration session expired or not found. Please start again.");
    }

    try {
      var credential = parseRegistrationResponseJson(request.credential());

      var result = relyingParty.finishRegistration(
          FinishRegistrationOptions.builder()
              .request(cachedOptions)
              .response(credential)
              .build()
      );

      var user = userRepository.findById(userId)
          .orElseThrow(() -> new ResourceNotFoundException("User not found"));

      var passkey = UserPasskey.builder()
          .user(user)
          .credentialId(result.getKeyId().getId().getBytes())
          .publicKey(result.getPublicKeyCose().getBytes())
          .signCount(result.getSignatureCount())
          .aaguid(result.getAaguid().getBytes())
          .deviceName(request.deviceName())
          .transports(extractTransports(credential))
          .isDiscoverable(result.isDiscoverable().orElse(false))
          .createdAt(now())
          .build();

      passkey = userPasskeyRepository.save(passkey);

      registrationCache.evict(cacheKey);

      return toResponse(passkey);
    } catch (Exception e) {
      log.warn("Error during passkey registration for user {}: {}", userId, getRootCauseMessage(e));
      throw new IllegalStateException("Failed to complete passkey registration");
    }
  }

  @Transactional(readOnly = true)
  public String startAuthentication(String email) {
    var optionsBuilder = StartAssertionOptions.builder()
        .timeout(300_000L) // 5 minutes
        .userVerification(UserVerificationRequirement.PREFERRED);

    // If email is provided, get credentials for that user
    if (isNotBlank(email)) {
      var userOtp = userRepository.findByEmail(email);
      if (userOtp.isPresent() && userOtp.get().getRoles() == ADMIN) {
        optionsBuilder.username(email);
      }
    }

    var assertionRequest = relyingParty.startAssertion(optionsBuilder.build());

    // Store assertion request in cache
    var authCache = getAuthenticationCache();
    var requestId = randomUUID().toString();
    authCache.put(requestId, assertionRequest);

    try {
      var optionsJson = assertionRequest.toCredentialsGetJson();

      // Include the requestId in the response so client can send it back
      return objectMapper.writeValueAsString(new AuthenticationStartResponse(
          requestId,
          objectMapper.readTree(optionsJson)
      ));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize authentication options");
    }
  }

  @Transactional
  public TokenResponse finishAuthentication(String requestId,
                                            PasskeyFinishAuthenticationRequest request,
                                            String remoteAddress,
                                            String userAgent) {
    var authCache = getAuthenticationCache();
    var assertionRequest = authCache.get(requestId, AssertionRequest.class);

    if (assertionRequest == null) {
      throw new AuthenticationServiceException("Authentication session expired or not found");
    }

    try {
      var credential =
          PublicKeyCredential.parseAssertionResponseJson(request.credential());

      var result = relyingParty.finishAssertion(
          FinishAssertionOptions.builder()
              .request(assertionRequest)
              .response(credential)
              .build()
      );

      if (!result.isSuccess()) {
        throw new AuthenticationServiceException("Passkey authentication failed");
      }

      // Get user from credential
      var passkey = userPasskeyRepository.findByCredentialIdWithUser(
              result.getCredential().getCredentialId().getBytes())
          .orElseThrow(() -> new AuthenticationServiceException("Credential not found"));

      var user = passkey.getUser();

      // Verify user is admin
      if (user.getRoles() != ADMIN) {
        throw new AuthenticationServiceException("Only admin users can use passkey authentication");
      }

      // Check if user is banned
      if (user.getBannedUntil() != null && user.getBannedUntil().isAfter(now())) {
        throw new AuthenticationServiceException("Account is banned until " + user.getBannedUntil());
      }

      // Update sign count and last used
      passkey.setSignCount(result.getSignatureCount());
      passkey.setLastUsedAt(now());
      userPasskeyRepository.save(passkey);

      // Clear authentication cache
      authCache.evict(requestId);

      // Generate JWT tokens
      var accessToken = jwtTokenProvider.generateAccessToken(
          user.getId(), user.getEmail(), user.getRoles().name());
      var refreshToken = refreshTokenService.createRefreshToken(user, remoteAddress, userAgent);

      return of(accessToken, refreshToken.getToken(), accessTokenExpiration / 1000);
    } catch (Exception e) {
      log.warn("Error during passkey authentication: {}", getRootCauseMessage(e));
      throw new AuthenticationServiceException("Failed to complete passkey authentication");
    }
  }

  @Transactional(readOnly = true)
  public List<PasskeyResponse> getUserPasskeys(Long userId) {
    return userPasskeyRepository.findByUserId(userId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public void deletePasskey(Long userId, UUID passkeyId) {
    var passkey = userPasskeyRepository.findById(passkeyId)
        .orElseThrow(() -> new ResourceNotFoundException("Passkey not found"));

    if (!passkey.getUser().getId().equals(userId)) {
      throw new IllegalStateException("Passkey does not belong to this user");
    }

    // Ensure user has at least one passkey or can still login with password
    var count = userPasskeyRepository.countByUserId(userId);
    if (count <= 1) {
      log.warn("User {} is deleting their last passkey", userId);
    }

    userPasskeyRepository.delete(passkey);
    log.info("Passkey {} deleted for user {}", passkeyId, userId);
  }

  @Transactional
  public PasskeyResponse updatePasskeyName(Long userId, UUID passkeyId, String newName) {
    var passkey = userPasskeyRepository.findById(passkeyId)
        .orElseThrow(() -> new ResourceNotFoundException("Passkey not found"));

    if (!passkey.getUser().getId().equals(userId)) {
      throw new IllegalStateException("Passkey does not belong to this user");
    }

    if (userPasskeyRepository.existsByUserIdAndDeviceName(userId, newName)) {
      throw new IllegalStateException("A passkey with this name already exists");
    }

    passkey.setDeviceName(newName);
    passkey = userPasskeyRepository.save(passkey);

    return toResponse(passkey);
  }

  private PasskeyResponse toResponse(UserPasskey passkey) {
    return new PasskeyResponse(
        passkey.getId(),
        passkey.getDeviceName(),
        passkey.getTransports(),
        passkey.getIsDiscoverable(),
        passkey.getLastUsedAt(),
        passkey.getCreatedAt()
    );
  }

  private Cache getRegistrationCache() {
    var cache = cacheManager.getCache(REGISTRATION_CACHE);
    if (cache == null) {
      throw new IllegalStateException("Registration cache not configured");
    }
    return cache;
  }

  private Cache getAuthenticationCache() {
    var cache = cacheManager.getCache(AUTHENTICATION_CACHE);
    if (cache == null) {
      throw new IllegalStateException("Authentication cache not configured");
    }
    return cache;
  }

  private List<String> extractTransports(
      PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> credential) {
    try {
      var transports = credential.getResponse().getTransports();
      if (isNotEmpty(transports)) {
        return transports.stream()
            .map(AuthenticatorTransport::getId)
            .toList();
      }
    } catch (Exception e) {
      log.warn("Could not extract transports: {}", e.getMessage());
    }
    return emptyList();
  }

  private byte[] longToBytes(Long value) {
    var bytes = new byte[8];
    for (var i = 7; i >= 0; i--) {
      bytes[i] = (byte) (value & 0xFF);
      value >>= 8;
    }
    return bytes;
  }

}

