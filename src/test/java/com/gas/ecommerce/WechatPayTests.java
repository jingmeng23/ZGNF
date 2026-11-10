package com.gas.ecommerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.modules.payment.wechat.*;
import com.gas.ecommerce.modules.payment.entity.Payment;
import com.gas.ecommerce.modules.payment.service.PaymentProviderGateway;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WechatPayTests {
    private final ObjectMapper json=new ObjectMapper();
    private WechatClient client;
    private WechatIdentityService identity;
    private WechatProperties config;
    private WechatPayGateway gateway;
    private Payment payment;
    @BeforeEach void setup() {
        client=mock(WechatClient.class); identity=mock(WechatIdentityService.class); config=new WechatProperties();
        config.setAppId("wx-test"); config.setMerchantId("mch-test"); config.setNotifyUrl("https://shop.example.com/api/v1/payment/wechat/notify");
        gateway=new WechatPayGateway(client,config,identity);
        payment=new Payment(); payment.setId(1L); payment.setPaymentNo("PAYTEST"); payment.setUserId(10L);
        payment.setAmount(new BigDecimal("12.34")); payment.setChannel("wechat_mini"); payment.setStatus("pending");
        when(identity.openId(10L)).thenReturn("openid-test");
        when(identity.paymentOpenId(10L)).thenReturn("openid-test");
    }
    private ObjectNode transaction() {
        return json.createObjectNode().put("appid","wx-test").put("mchid","mch-test").put("out_trade_no","PAYTEST")
                .put("transaction_id","WXTRADE1").put("trade_state","SUCCESS").put("success_time",OffsetDateTime.now().toString())
                .set("amount",json.createObjectNode().put("total",1234).put("currency","CNY"));
    }
    private ObjectNode valid() { var r=transaction(); r.set("payer",json.createObjectNode().put("openid","openid-test")); return r; }
    @Test void centsAreExactAndRejectFractionOverflowAndNonpositive() {
        assertEquals(1234,WechatPayGateway.fen(new BigDecimal("12.34")));
        assertThrows(ArithmeticException.class,()->WechatPayGateway.fen(new BigDecimal("1.001")));
        assertThrows(ArithmeticException.class,()->WechatPayGateway.fen(new BigDecimal("9999999999999")));
        assertThrows(BusinessException.class,()->WechatPayGateway.fen(BigDecimal.ZERO));
        assertThrows(BusinessException.class,()->WechatPayGateway.fen(new BigDecimal("-1")));
    }
    @Test void verifiedSuccessMapsToExistingOutcomeContract() {
        var result=gateway.verifiedSnapshot(payment,valid());
        assertEquals(PaymentProviderGateway.State.SUCCESS,result.state());
        assertEquals("PAYTEST",result.outcome().paymentNo());
        assertEquals(new BigDecimal("12.34"),result.outcome().amount());
        assertEquals("WXTRADE1",result.outcome().transactionNo());
    }
    @Test void mismatchedMerchantAppOrderPayerCurrencyAndAmountAreRejected() {
        for(String field:new String[]{"appid","mchid","out_trade_no"}) {
            var value=valid().put(field,"attacker"); assertThrows(BusinessException.class,()->gateway.verifiedSnapshot(payment,value));
        }
        var payer=valid(); ((ObjectNode)payer.get("payer")).put("openid","other-user");
        assertThrows(BusinessException.class,()->gateway.verifiedSnapshot(payment,payer));
        var amount=valid(); ((ObjectNode)amount.get("amount")).put("total",1);
        assertThrows(BusinessException.class,()->gateway.verifiedSnapshot(payment,amount));
        var currency=valid(); ((ObjectNode)currency.get("amount")).put("currency","USD");
        assertThrows(BusinessException.class,()->gateway.verifiedSnapshot(payment,currency));
        var fractional=valid(); ((ObjectNode)fractional.get("amount")).put("total",1234.5);
        assertThrows(BusinessException.class,()->gateway.verifiedSnapshot(payment,fractional));
    }
    @Test void pendingIsNotReportedAsClosedAndUnknownIsNotSuccess() {
        assertEquals(PaymentProviderGateway.State.PENDING,gateway.verifiedSnapshot(payment,valid().put("trade_state","PAYERROR")).state());
        assertEquals(PaymentProviderGateway.State.UNKNOWN,gateway.verifiedSnapshot(payment,valid().put("trade_state","UNKNOWN")).state());
        assertThrows(BusinessException.class,()->gateway.verifiedSnapshot(payment,valid().put("transaction_id","")));
    }
    @Test void prepayUsesServerAmountBoundPayerAndStablePaymentIdentity() {
        when(client.request(eq("POST"),eq("/v3/pay/transactions/jsapi"),any())).thenAnswer(call->{
            var body=(java.util.Map<?,?>)call.getArgument(2);
            assertEquals("PAYTEST",body.get("out_trade_no"));
            assertEquals(1234,((java.util.Map<?,?>)body.get("amount")).get("total"));
            assertEquals("openid-test",((java.util.Map<?,?>)body.get("payer")).get("openid"));
            return json.createObjectNode().put("prepay_id","prepay1");
        });
        gateway.prepay(payment,LocalDateTime.now().plusMinutes(10));
        verify(client).paymentParameters("prepay1");
    }
    @Test void missingPrepayOrHttpCallbackDoesNotProduceClientPaymentParameters() {
        when(client.request(anyString(),anyString(),any())).thenReturn(json.createObjectNode());
        assertThrows(BusinessException.class,()->gateway.prepay(payment,LocalDateTime.now()));
        config.setNotifyUrl("http://shop.invalid/notify");
        assertThrows(BusinessException.class,()->gateway.prepay(payment,LocalDateTime.now()));
        verify(client,never()).paymentParameters(anyString());
    }
    @Test void failedCloseCannotReleaseResourcesWhileProviderIsStillPending() {
        when(client.request(eq("POST"),contains("/close"),any())).thenThrow(new BusinessException("timeout"));
        when(client.request(eq("GET"),contains("out-trade-no"),isNull())).thenReturn(valid().put("trade_state","NOTPAY"));
        assertEquals(PaymentProviderGateway.State.PENDING,gateway.close(payment).state());
    }
    @Test void signedOrderNotExistEstablishesClosure() {
        when(client.request(eq("POST"),contains("/close"),any())).thenThrow(new WechatClient.ProviderException("ORDER_NOT_EXIST"));
        assertEquals(PaymentProviderGateway.State.CLOSED,gateway.close(payment).state());
    }
    @Test void foreignChannelNeverUsesWechatNetwork() {
        payment.setChannel("test");
        assertEquals(PaymentProviderGateway.State.UNKNOWN,gateway.query(payment).state());
        assertEquals(PaymentProviderGateway.State.UNKNOWN,gateway.close(payment).state());
        assertFalse(gateway.refund(payment,"R1")); verifyNoInteractions(client);
    }
    @Test void refundQueriesSameIdentityAndRequiresConfirmedAmount() {
        var refund=json.createObjectNode().put("status","SUCCESS").put("out_refund_no","WXAS1").put("out_trade_no","PAYTEST");
        refund.set("amount",json.createObjectNode().put("total",1234).put("refund",1234).put("currency","CNY"));
        when(client.request(eq("GET"),eq("/v3/refund/domestic/refunds/WXAS1"),isNull())).thenReturn(refund);
        assertTrue(gateway.refund(payment,"WXAS1"));
        refund.put("status","PROCESSING"); assertFalse(gateway.refund(payment,"WXAS1"));
        refund.put("status","SUCCESS"); ((ObjectNode)refund.get("amount")).put("refund",1);
        assertFalse(gateway.refund(payment,"WXAS1"));
        verify(client,never()).request(eq("POST"),anyString(),any());
    }
    @Test void rsaVerificationRejectsTamperingExpiredAndDifferentKey() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var pair=generator.generateKeyPair(); String time=String.valueOf(Instant.now().getEpochSecond()),nonce="nonce",body="{\"amount\":1234}";
        String signature=WechatCrypto.sign(pair.getPrivate(),time+"\n"+nonce+"\n"+body+"\n");
        assertDoesNotThrow(()->WechatCrypto.verify(pair.getPublic(),time,nonce,signature,body));
        assertThrows(GeneralSecurityException.class,()->WechatCrypto.verify(pair.getPublic(),time,nonce,signature,body+" "));
        assertThrows(GeneralSecurityException.class,()->WechatCrypto.verify(pair.getPublic(),"1",nonce,signature,body));
        var other=generator.generateKeyPair();
        assertThrows(GeneralSecurityException.class,()->WechatCrypto.verify(other.getPublic(),time,nonce,signature,body));
    }
    @Test void aeadDecryptionAuthenticatesCiphertextAndAssociatedData() throws Exception {
        String key="01234567890123456789012345678901",nonce="012345678901",aad="transaction",plain="{\"trade_state\":\"SUCCESS\"}";
        var cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"AES"),new GCMParameterSpec(128,nonce.getBytes(StandardCharsets.UTF_8)));
        cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8)); String ciphertext=Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        assertEquals(plain,WechatCrypto.decrypt(key,nonce,aad,ciphertext));
        assertThrows(GeneralSecurityException.class,()->WechatCrypto.decrypt(key,nonce,"other",ciphertext));
        assertThrows(GeneralSecurityException.class,()->WechatCrypto.decrypt("bad",nonce,aad,ciphertext));
    }
}
