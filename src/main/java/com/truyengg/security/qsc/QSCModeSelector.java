package com.truyengg.security.qsc;

import com.truyengg.service.config.QSCSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class QSCModeSelector {

  private final QSCSettingsService qscSettings;

  public QSCMode getMode() {
    return qscSettings.isHPKEEnabled() ? QSCMode.QUANTUM_SAFE : QSCMode.CLASSICAL;
  }

  public boolean shouldEncryptPayload() {
    return qscSettings.isHPKEEnabled();
  }

  public boolean shouldUseQuantumSafeJWT() {
    return qscSettings.isHPKEEnabled();
  }

  public String getJWTAlgorithm(TokenType tokenType) {
    if (!qscSettings.isHPKEEnabled()) {
      return "HS256";
    }

    return tokenType == TokenType.ACCESS
        ? qscSettings.getAccessTokenAlgorithm()
        : qscSettings.getRefreshTokenAlgorithm();
  }

  public enum QSCMode {
    CLASSICAL,
    QUANTUM_SAFE
  }

  public enum TokenType {
    ACCESS,
    REFRESH
  }
}

