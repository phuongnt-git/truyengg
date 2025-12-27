package com.truyengg.security.qsc.model;

import lombok.Builder;

import java.security.PublicKey;
import java.time.ZonedDateTime;

@Builder
public record DilithiumPublicKeyInfo(
    Long id,
    String algorithm,
    byte[] publicKeyBytes,
    PublicKey publicKey,
    String fingerprint,
    ZonedDateTime expiresAt
) {
}

