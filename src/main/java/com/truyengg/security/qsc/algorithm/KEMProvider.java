package com.truyengg.security.qsc.algorithm;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

public interface KEMProvider {
  String getAlgorithm();

  int getPublicKeySize();

  int getCiphertextSize();

  int getSharedSecretSize();

  KeyPair generateKeyPair() throws GeneralSecurityException;

  byte[] encapsulate(PublicKey publicKey, byte[] sharedSecret) throws GeneralSecurityException;

  byte[] decapsulate(PrivateKey privateKey, byte[] ciphertext) throws GeneralSecurityException;
}

