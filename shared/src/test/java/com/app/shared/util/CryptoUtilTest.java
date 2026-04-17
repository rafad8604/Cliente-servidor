package com.app.shared.util;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CryptoUtilTest {

    @Test
    void encryptDecryptRoundTrip() throws Exception {
        SecretKey key = CryptoUtil.generateAESKey();
        byte[] iv = CryptoUtil.generateIV();
        byte[] original = "mensaje de prueba con acentos áéíóú".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = CryptoUtil.encrypt(original, key, iv);
        assertNotEquals(new String(original, StandardCharsets.UTF_8), new String(encrypted, StandardCharsets.UTF_8));

        byte[] decrypted = CryptoUtil.decrypt(encrypted, key, iv);
        assertArrayEquals(original, decrypted);
    }

    @Test
    void keyBase64RoundTrip() throws Exception {
        SecretKey key = CryptoUtil.generateAESKey();
        String encoded = CryptoUtil.keyToBase64(key);
        SecretKey decoded = CryptoUtil.keyFromBase64(encoded);

        assertArrayEquals(key.getEncoded(), decoded.getEncoded());
    }

    @Test
    void ivBase64RoundTrip() {
        byte[] iv = CryptoUtil.generateIV();
        String encoded = CryptoUtil.ivToBase64(iv);
        byte[] decoded = CryptoUtil.ivFromBase64(encoded);

        assertTrue(Arrays.equals(iv, decoded));
    }
}
