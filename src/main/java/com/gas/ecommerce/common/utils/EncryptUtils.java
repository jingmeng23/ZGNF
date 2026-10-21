package com.gas.ecommerce.common.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.security.MessageDigest;
import java.security.SecureRandom;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class EncryptUtils {
    private EncryptUtils() { }
    public static String encryptAes(String value, String key) {
        try {
            byte[] nonce = new byte[12]; new SecureRandom().nextBytes(nonce);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(normalizeKey(key), "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = c.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] result = java.util.Arrays.copyOf(nonce, nonce.length + encrypted.length);
            System.arraycopy(encrypted, 0, result, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        }
        catch (Exception ex) { throw new IllegalStateException("AES encryption failed", ex); }
    }
    public static String decryptAes(String value, String key) {
        try {
            byte[] input = Base64.getDecoder().decode(value);
            byte[] nonce = java.util.Arrays.copyOf(input, 12);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(normalizeKey(key), "AES"), new GCMParameterSpec(128, nonce));
            return new String(c.doFinal(input, nonce.length, input.length - nonce.length), StandardCharsets.UTF_8);
        }
        catch (Exception ex) { throw new IllegalStateException("AES decryption failed", ex); }
    }
    public static String bcrypt(String value) { return new BCryptPasswordEncoder().encode(value); }
    public static boolean matchesBcrypt(String value, String hash) { return new BCryptPasswordEncoder().matches(value, hash); }
    public static String fingerprint(String value, String key) {
        try {
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("Fingerprint failed", ex); }
    }
    private static byte[] normalizeKey(String key) throws Exception { return MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)); }
}
