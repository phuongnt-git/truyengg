package com.truyengg.security.passkey.model;

public record AuthenticationStartResponse(
    String requestId,
    Object publicKeyCredentialRequestOptions
) {
}
