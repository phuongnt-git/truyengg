package com.truyengg.security.qsc;

import com.truyengg.security.qsc.algorithm.Dilithium3Provider;
import com.truyengg.security.qsc.algorithm.KEMProvider;
import com.truyengg.security.qsc.algorithm.Kyber1024Provider;
import com.truyengg.security.qsc.algorithm.SignatureProvider;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class QSCAlgorithmRegistry {

  private final Map<String, KEMProvider> kemProviders = new ConcurrentHashMap<>();
  private final Map<String, SignatureProvider> signatureProviders = new ConcurrentHashMap<>();

  @PostConstruct
  public void initialize() {
    // Register KEM algorithms
    registerKEM("KYBER1024", new Kyber1024Provider());
    // Future algorithms can be added here:
    // registerKEM("KYBER768", new Kyber768Provider());
    // registerKEM("KYBER512", new Kyber512Provider());

    // Register signature algorithms
    registerSignature("DILITHIUM3", new Dilithium3Provider());
    // Future algorithms:
    // registerSignature("DILITHIUM2", new Dilithium2Provider());
    // registerSignature("DILITHIUM5", new Dilithium5Provider());

    log.info("[QSC] Algorithm registry initialized: KEM={}, Signature={}",
        kemProviders.size(), signatureProviders.size());
  }

  @Cacheable(value = "qsc:algorithms#24h", key = "'kem-' + #algorithm")
  public KEMProvider getKEMProvider(String algorithm) {
    var provider = kemProviders.get(algorithm);
    if (provider == null) {
      throw new UnsupportedOperationException("Unsupported KEM algorithm: " + algorithm);
    }
    return provider;
  }

  @Cacheable(value = "qsc:algorithms#24h", key = "'sig-' + #algorithm")
  public SignatureProvider getSignatureProvider(String algorithm) {
    var provider = signatureProviders.get(algorithm);
    if (provider == null) {
      throw new UnsupportedOperationException("Unsupported signature algorithm: " + algorithm);
    }
    return provider;
  }

  public List<String> getSupportedKEMAlgorithms() {
    return new ArrayList<>(kemProviders.keySet());
  }

  public List<String> getSupportedSignatureAlgorithms() {
    return new ArrayList<>(signatureProviders.keySet());
  }

  private void registerKEM(String algorithm, KEMProvider provider) {
    kemProviders.put(algorithm, provider);
  }

  private void registerSignature(String algorithm, SignatureProvider provider) {
    signatureProviders.put(algorithm, provider);
  }
}

