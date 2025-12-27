package com.truyengg.security.qsc.model;

import lombok.Builder;

import java.time.ZonedDateTime;

@Builder
public record KyberPublicKeyInfo(
    Long id,
    String algorithm,
    byte[] publicKeyBytes,
    String fingerprint,
    ZonedDateTime expiresAt
) {
}

