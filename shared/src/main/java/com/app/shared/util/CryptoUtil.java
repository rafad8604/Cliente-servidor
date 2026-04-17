package com.app.shared.util;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * Utilidad de criptografía para SHA-256 (Hash) y AES-256 (Encriptación).
 * Usa CipherInputStream/CipherOutputStream para procesar archivos gigantes sin saturar RAM.
 */
public final class CryptoUtil {

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String SHA_ALGORITHM = "SHA-256";
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int AES_KEY_SIZE = 256;
    private static final int IV_SIZE = 16;
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int BUFFER_SIZE = 8192; // 8KB buffer para streaming

    private CryptoUtil() {
        // Utility class
    }

    // ==================== SHA-256 ====================

    /**
     * Calcula el hash SHA-256 de un InputStream leyendo en bloques de 8KB.
     * No carga el archivo completo en memoria.
     */
    public static String hashSHA256(InputStream inputStream) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance(SHA_ALGORITHM);
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
        }
        return bytesToHex(digest.digest());
    }

    /**
     * Calcula el hash SHA-256 de un archivo.
     */
    public static String hashSHA256(File file) throws NoSuchAlgorithmException, IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return hashSHA256(fis);
        }
    }

    /**
     * Calcula el hash SHA-256 de un String.
     */
    public static String hashSHA256(String text) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(SHA_ALGORITHM);
        byte[] hash = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    // ==================== AES-256 ====================

    /**
     * Genera una clave AES-256 aleatoria.
     */
    public static SecretKey generateAESKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance(AES_ALGORITHM);
        keyGen.init(AES_KEY_SIZE, new SecureRandom());
        return keyGen.generateKey();
    }

    /**
     * Deriva una clave AES-256 a partir de una contraseña y un salt usando PBKDF2.
     */
    public static SecretKey deriveAESKey(String password, byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_SIZE);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), AES_ALGORITHM);
    }

    /**
     * Genera un IV aleatorio de 16 bytes para AES/CBC.
     */
    public static byte[] generateIV() {
        byte[] iv = new byte[IV_SIZE];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    /**
     * Genera un salt aleatorio de 16 bytes para PBKDF2.
     */
    public static byte[] generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    /**
     * Crea un CipherInputStream para ENCRIPTAR datos sobre la marcha.
     * Lee del InputStream original y produce bytes encriptados.
     */
    public static CipherInputStream encryptStream(InputStream input, SecretKey key, byte[] iv)
            throws NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidKeyException, InvalidAlgorithmParameterException {
        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        return new CipherInputStream(input, cipher);
    }

    /**
     * Crea un CipherOutputStream para DESCIFRAR datos al escribir.
     * Escribe bytes encriptados y produce datos originales en el OutputStream.
     */
    public static CipherOutputStream decryptStream(OutputStream output, SecretKey key, byte[] iv)
            throws NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidKeyException, InvalidAlgorithmParameterException {
        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        return new CipherOutputStream(output, cipher);
    }

    /**
     * Encripta un array de bytes completo (para mensajes pequeños).
     */
    public static byte[] encrypt(byte[] data, SecretKey key, byte[] iv)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        return cipher.doFinal(data);
    }

    /**
     * Desencripta un array de bytes completo (para mensajes pequeños).
     */
    public static byte[] decrypt(byte[] encryptedData, SecretKey key, byte[] iv)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        return cipher.doFinal(encryptedData);
    }

    /**
     * Encripta un archivo completo usando streams. Escribe IV al inicio del archivo de salida.
     * Formato: [16 bytes IV][datos encriptados...]
     */
    public static String encryptFile(File input, File output, SecretKey key)
            throws GeneralSecurityException, IOException {
        byte[] iv = generateIV();

        try (FileInputStream fis = new FileInputStream(input);
             FileOutputStream fos = new FileOutputStream(output)) {

            // Escribir IV al inicio
            fos.write(iv);

            // Encriptar el stream
            try (CipherInputStream cis = encryptStream(fis, key, iv)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = cis.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
        }

        // Calcular hash del archivo original
        return hashSHA256(input);
    }

    /**
     * Desencripta un archivo completo usando streams. Lee IV de los primeros 16 bytes.
     */
    public static void decryptFile(File input, File output, SecretKey key)
            throws GeneralSecurityException, IOException {
        try (FileInputStream fis = new FileInputStream(input);
             FileOutputStream fos = new FileOutputStream(output)) {

            // Leer IV del inicio
            byte[] iv = new byte[IV_SIZE];
            if (fis.read(iv) != IV_SIZE) {
                throw new IOException("No se pudo leer el IV del archivo encriptado");
            }

            // Desencriptar al escribir
            try (CipherOutputStream cos = decryptStream(fos, key, iv)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    cos.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    // ==================== Utilidades de Clave ====================

    /**
     * Serializa una SecretKey a Base64 para transmisión por red.
     */
    public static String keyToBase64(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * Reconstruye una SecretKey desde Base64.
     */
    public static SecretKey keyFromBase64(String base64Key) {
        byte[] decoded = Base64.getDecoder().decode(base64Key);
        return new SecretKeySpec(decoded, AES_ALGORITHM);
    }

    /**
     * Codifica un IV a Base64.
     */
    public static String ivToBase64(byte[] iv) {
        return Base64.getEncoder().encodeToString(iv);
    }

    /**
     * Decodifica un IV desde Base64.
     */
    public static byte[] ivFromBase64(String base64Iv) {
        return Base64.getDecoder().decode(base64Iv);
    }

    // ==================== Utils privados ====================

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
