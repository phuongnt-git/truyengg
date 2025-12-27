package com.truyengg.controller.api.admin;

import com.truyengg.model.response.ApiResponse;
import com.truyengg.security.qsc.QSCKeyManager;
import com.truyengg.service.config.QSCSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Admin QSC", description = "Quantum-Safe Cryptography management")
@RestController
@RequestMapping("/api/admin/qsc")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminQSCController {

  private final QSCKeyManager keyManager;
  private final QSCSettingsService qscSettings;

  @GetMapping("/status")
  @Operation(summary = "Get QSC status", description = "Get current QSC configuration and key status")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus() {
    var kyberKey = keyManager.getCurrentKyberPublicKey();
    var accessKey = keyManager.getDilithiumPublicKeyForAccess();
    var refreshKey = keyManager.getDilithiumPublicKeyForRefresh();

    var status = Map.of(
        "enabled", qscSettings.isHPKEEnabled(),
        "kemAlgorithm", qscSettings.getKemAlgorithm(),
        "currentKeys", Map.of(
            "kyber", Map.of("id", kyberKey.id(), "algorithm", kyberKey.algorithm(), "expiresAt", kyberKey.expiresAt()),
            "dilithiumAccess", Map.of("id", accessKey.id(), "algorithm", accessKey.algorithm(), "expiresAt", accessKey.expiresAt()),
            "dilithiumRefresh", Map.of("id", refreshKey.id(), "algorithm", refreshKey.algorithm(), "expiresAt", refreshKey.expiresAt())
        ),
        "nextRotation", keyManager.getNextRotationTime(),
        "rotationEnabled", qscSettings.isKeyRotationEnabled()
    );

    return ResponseEntity.ok(ApiResponse.success(status));
  }

  @PostMapping("/rotate-keys")
  @Operation(summary = "Rotate keys", description = "Force immediate key rotation")
  public ResponseEntity<ApiResponse<Object>> rotateKeys() {
    keyManager.rotateKeys();
    return ResponseEntity.ok(ApiResponse.success("Keys rotated successfully"));
  }

  @PostMapping("/enable")
  @Operation(summary = "Enable QSC", description = "Enable or disable QSC encryption")
  public ResponseEntity<ApiResponse<Object>> enableQSC(
      @RequestParam boolean enabled,
      Authentication authentication) {

    var userId = Long.parseLong(authentication.getName());
    qscSettings.enableHPKE(enabled, userId);

    return ResponseEntity.ok(ApiResponse.success("QSC " + (enabled ? "enabled" : "disabled")));
  }
}

