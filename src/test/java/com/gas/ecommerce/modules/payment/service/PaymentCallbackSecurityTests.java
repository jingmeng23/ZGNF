package com.gas.ecommerce.modules.payment.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;

class PaymentCallbackSecurityTests {
    static final String SECRET="test-only-payment-callback-secret-2026";
    PaymentCallbackVerifier verifier=new PaymentCallbackVerifier(SECRET);
    String signature(String timestamp,String body) throws Exception {
        var mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(("test\n"+timestamp+"\n"+body).getBytes(StandardCharsets.UTF_8)));
    }
    @Test void authenticBodyIsAcceptedAndTamperingOrChannelSwapIsDenied() throws Exception {
        String now=Long.toString(Instant.now().getEpochSecond()), body="{\"amount\":50}";
        String sig=signature(now,body);
        assertDoesNotThrow(()->verifier.verify("test",now,sig,body));
        assertThrows(AccessDeniedException.class,()->verifier.verify("test",now,sig,body+" "));
        assertThrows(AccessDeniedException.class,()->verifier.verify("other",now,sig,body));
    }
    @Test void expiredTimestampMalformedSignatureAndWeakSecretAreDenied() throws Exception {
        String old=Long.toString(Instant.now().minusSeconds(301).getEpochSecond()),sig=signature(old,"{}");
        assertThrows(AccessDeniedException.class,()->verifier.verify("test",old,sig,"{}"));
        String now=Long.toString(Instant.now().getEpochSecond());
        assertThrows(AccessDeniedException.class,()->verifier.verify("test",now,"xyz","{}"));
        assertThrows(AccessDeniedException.class,()->new PaymentCallbackVerifier("").verify("test",now,sig,"{}"));
    }
    @Test void actualPeerMustMatchConfiguredCidrAndEmptyConfigurationDeniesAll() {
        var sources=new PaymentCallbackSourcePolicy("10.20.0.0/16,::1");
        assertDoesNotThrow(()->sources.verify("10.20.4.1")); assertDoesNotThrow(()->sources.verify("::1"));
        assertThrows(AccessDeniedException.class,()->sources.verify("10.21.4.1"));
        assertThrows(AccessDeniedException.class,()->new PaymentCallbackSourcePolicy("").verify("127.0.0.1"));
    }
}
