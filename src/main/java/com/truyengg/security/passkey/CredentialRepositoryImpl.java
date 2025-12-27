package com.truyengg.security.passkey;

import com.truyengg.domain.entity.User;
import com.truyengg.domain.entity.UserPasskey;
import com.truyengg.domain.repository.UserPasskeyRepository;
import com.truyengg.domain.repository.UserRepository;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.AuthenticatorTransport;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.PublicKeyCredentialType;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptySet;

@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CredentialRepositoryImpl implements CredentialRepository {

  UserPasskeyRepository userPasskeyRepository;
  UserRepository userRepository;

  @Override
  public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
    var passkeys = userPasskeyRepository.findByUserEmail(username);
    return passkeys.stream()
        .map(this::toCredentialDescriptor)
        .collect(Collectors.toSet());
  }

  @Override
  public Optional<ByteArray> getUserHandleForUsername(String username) {
    return userRepository.findByEmail(username)
        .map(user -> new ByteArray(longToBytes(user.getId())));
  }

  @Override
  public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
    var userId = bytesToLong(userHandle.getBytes());
    return userRepository.findById(userId)
        .map(User::getEmail);
  }

  @Override
  public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
    return userPasskeyRepository.findByCredentialId(credentialId.getBytes())
        .filter(passkey -> {
          var userId = bytesToLong(userHandle.getBytes());
          return passkey.getUser().getId().equals(userId);
        })
        .map(this::toRegisteredCredential);
  }

  @Override
  public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
    return userPasskeyRepository.findByCredentialId(credentialId.getBytes())
        .map(this::toRegisteredCredential)
        .map(Set::of)
        .orElse(emptySet());
  }

  private PublicKeyCredentialDescriptor toCredentialDescriptor(UserPasskey passkey) {
    var builder = PublicKeyCredentialDescriptor.builder()
        .id(new ByteArray(passkey.getCredentialId()))
        .type(PublicKeyCredentialType.PUBLIC_KEY);

    if (passkey.getTransports() != null && !passkey.getTransports().isEmpty()) {
      builder.transports(passkey.getTransports().stream()
          .map(transport -> {
            try {
              return AuthenticatorTransport.valueOf(transport.toUpperCase());
            } catch (IllegalArgumentException e) {
              log.warn("Unknown transport type: {}", transport);
              return null;
            }
          })
          .filter(ObjectUtils::isNotEmpty)
          .collect(Collectors.toUnmodifiableSet()));
    }

    return builder.build();
  }

  private RegisteredCredential toRegisteredCredential(UserPasskey passkey) {
    return RegisteredCredential.builder()
        .credentialId(new ByteArray(passkey.getCredentialId()))
        .userHandle(new ByteArray(longToBytes(passkey.getUser().getId())))
        .publicKeyCose(new ByteArray(passkey.getPublicKey()))
        .signatureCount(passkey.getSignCount())
        .build();
  }

  private byte[] longToBytes(Long value) {
    var bytes = new byte[8];
    for (var i = 7; i >= 0; i--) {
      bytes[i] = (byte) (value & 0xFF);
      value >>= 8;
    }
    return bytes;
  }

  private Long bytesToLong(byte[] bytes) {
    var result = 0L;
    for (var b : bytes) {
      result = (result << 8) | (b & 0xFF);
    }
    return result;
  }
}

