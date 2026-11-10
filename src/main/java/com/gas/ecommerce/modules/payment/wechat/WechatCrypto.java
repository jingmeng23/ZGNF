package com.gas.ecommerce.modules.payment.wechat;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.*;

/** WeChat Pay API v3 RSA signatures and authenticated notification decryption. */
public final class WechatCrypto {
    private WechatCrypto() {}
    public static PrivateKey privateKey(String path) throws Exception {
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pem(path, "PRIVATE KEY")));
    }
    public static PublicKey publicKey(String path) throws Exception {
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pem(path, "PUBLIC KEY")));
    }
    private static byte[] pem(String path, String type) throws Exception {
        return Base64.getDecoder().decode(Files.readString(Path.of(path))
                .replace("-----BEGIN " + type + "-----", "").replace("-----END " + type + "-----", "").replaceAll("\\s", ""));
    }
    public static String sign(PrivateKey key, String message) throws Exception {
        Signature signature=Signature.getInstance("SHA256withRSA"); signature.initSign(key);
        signature.update(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }
    public static void verify(PublicKey key, String timestamp, String nonce, String signature, String body) throws Exception {
        long seconds=Long.parseLong(timestamp);
        if (Math.abs(Instant.now().getEpochSecond()-seconds)>300 || nonce==null || nonce.isBlank())
            throw new GeneralSecurityException("Stale or invalid WeChat signature");
        Signature verifier=Signature.getInstance("SHA256withRSA"); verifier.initVerify(key);
        verifier.update((timestamp+"\n"+nonce+"\n"+body+"\n").getBytes(StandardCharsets.UTF_8));
        if (!verifier.verify(Base64.getDecoder().decode(signature))) throw new GeneralSecurityException("Invalid WeChat signature");
    }
    public static String decrypt(String key, String nonce, String associatedData, String ciphertext) throws Exception {
        if (key.getBytes(StandardCharsets.UTF_8).length!=32) throw new GeneralSecurityException("API v3 key must be 32 bytes");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"AES"),
                new GCMParameterSpec(128,nonce.getBytes(StandardCharsets.UTF_8)));
        cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)),StandardCharsets.UTF_8);
    }
}
