package com.truyengg.security.qsc.algorithm;

import lombok.extern.slf4j.Slf4j;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

@Slf4j
public class Dilithium3Provider implements SignatureProvider {

  private static final String ALGORITHM = "DILITHIUM3";
  private static final int SIGNATURE_SIZE = 3293;

  @Override
  public String getAlgorithm() {
    return ALGORITHM;
  }

  @Override
  public int getSignatureSize() {
    return SIGNATURE_SIZE;
  }

  @Override
  public KeyPair generateKeyPair() throws GeneralSecurityException {
    var keyGen = KeyPairGenerator.getInstance(ALGORITHM, "BCPQC");
    return keyGen.generateKeyPair();
  }

  @Override
  public byte[] sign(PrivateKey privateKey, byte[] data) throws GeneralSecurityException {
    var signer = Signature.getInstance(ALGORITHM, "BCPQC");
    signer.initSign(privateKey);
    signer.update(data);
    return signer.sign();
  }

  @Override
  public boolean verify(PublicKey publicKey, byte[] data, byte[] signature) throws GeneralSecurityException {
    var verifier = Signature.getInstance(ALGORITHM, "BCPQC");
    verifier.initVerify(publicKey);
    verifier.update(data);
    return verifier.verify(signature);
  }
}

