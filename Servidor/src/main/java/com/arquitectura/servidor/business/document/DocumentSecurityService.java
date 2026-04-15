package com.arquitectura.servidor.business.document;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

@Service
public class DocumentSecurityService {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public DocumentSecurityService(DocumentProcessingConfig config) {
        this.secretKey = new SecretKeySpec(sha256(config.getEncryptionSecret().getBytes(StandardCharsets.UTF_8)), "AES");
    }

    public MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo crear MessageDigest SHA-256", e);
        }
    }

    public byte[] generateInitializationVector() {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);
        return iv;
    }

    public Cipher newEncryptCipher(byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return cipher;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo inicializar el cifrado de documentos", e);
        }
    }

    public String getEncryptionAlgorithmLabel() {
        return "AES/GCM/NoPadding (clave derivada con SHA-256)";
    }

    private byte[] sha256(byte[] payload) {
        MessageDigest digest = newSha256Digest();
        return digest.digest(payload);
    }
}

