package com.truyengg.controller.api;

import com.truyengg.model.response.ApiResponse;
import com.truyengg.security.qsc.QSCKeyManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

@Tag(name = "QSC Public Key", description = "Quantum-safe public key distribution")
@RestController
@RequestMapping("/api/qsc")
@RequiredArgsConstructor
public class QSCPublicKeyController {

  private final QSCKeyManager keyManager;

  @GetMapping("/public-key")
  @Operation(summary = "Get public key", description = "Get current Kyber public key for HPKE encryption")
  public ResponseEntity<ApiResponse<PublicKeyResponse>> getPublicKey() {
    var keyInfo = keyManager.getCurrentKyberPublicKey();

    var response = PublicKeyResponse.builder()
        .keyId(keyInfo.id())
        .algorithm(keyInfo.algorithm())
        .publicKey(Base64.getEncoder().encodeToString(keyInfo.publicKeyBytes()))
        .expiresAt(keyInfo.expiresAt().toString())
        .build();

    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @Builder
  record PublicKeyResponse(
      Long keyId,
      String algorithm,
      String publicKey,
      String expiresAt
  ) {
  }
}

