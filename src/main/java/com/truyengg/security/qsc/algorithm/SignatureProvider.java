package com.truyengg.security.qsc.algorithm;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

public interface SignatureProvider {
  String getAlgorithm();

  int getSignatureSize();

  KeyPair generateKeyPair() throws GeneralSecurityException;

  byte[] sign(PrivateKey privateKey, byte[] data) throws GeneralSecurityException;

  boolean verify(PublicKey publicKey, byte[] data, byte[] signature) throws GeneralSecurityException;
}

