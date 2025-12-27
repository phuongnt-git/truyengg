package com.truyengg.security.config;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
@RequiredArgsConstructor
public class PasskeyConfig {

  @Value("${truyengg.passkey.rp-id:localhost}")
  private String rpId;

  @Value("${truyengg.passkey.rp-name:TruyenGG Admin}")
  private String rpName;

  @Value("${truyengg.passkey.origins:http://localhost:8080}")
  private Set<String> allowedOrigins;

  @Bean
  public RelyingPartyIdentity relyingPartyIdentity() {
    return RelyingPartyIdentity.builder()
        .id(rpId)
        .name(rpName)
        .build();
  }

  @Bean
  public RelyingParty relyingParty(RelyingPartyIdentity rpIdentity,
                                   CredentialRepository credentialRepository) {
    return RelyingParty.builder()
        .identity(rpIdentity)
        .credentialRepository(credentialRepository)
        .origins(allowedOrigins)
        .build();
  }
}

