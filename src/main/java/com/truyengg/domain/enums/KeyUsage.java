package com.truyengg.domain.enums;

public enum KeyUsage {
  HPKE,          // For request/response encryption
  JWT_ACCESS,    // For access token signatures
  JWT_REFRESH    // For refresh token signatures
}

