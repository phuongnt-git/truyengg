package com.truyengg.security.passkey.model;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record PasskeyResponse(
    UUID id,
    String deviceName,
    List<String> transports,
    Boolean isDiscoverable,
    ZonedDateTime lastUsedAt,
    ZonedDateTime createdAt
) {
}

