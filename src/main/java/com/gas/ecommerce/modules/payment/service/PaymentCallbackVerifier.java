package com.gas.ecommerce.modules.payment.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Internal payment gateway protocol; this is not a WeChat/Alipay signature verifier. */
@Component
public class PaymentCallbackVerifier {
    private final String secret;
    public PaymentCallbackVerifier(@Value("${app.payments.callback-secret:}") String secret) {this.secret=secret;}
    public void verify(String channel,String timestamp,String signature,String body) {
        try {
            long seconds=Long.parseLong(timestamp);
            long now=Instant.now().getEpochSecond();
            if(secret.getBytes(StandardCharsets.UTF_8).length<32 || seconds<now-300 || seconds>now+300) throw new IllegalArgumentException();
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            byte[] expected=mac.doFinal((channel+"\n"+timestamp+"\n"+body).getBytes(StandardCharsets.UTF_8));
            if(!MessageDigest.isEqual(expected,HexFormat.of().parseHex(signature))) throw new IllegalArgumentException();
        } catch(Exception ex) {throw new AccessDeniedException("支付回调签名无效或已过期");}
    }
}
