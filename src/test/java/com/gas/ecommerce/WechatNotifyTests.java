package com.gas.ecommerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.ecommerce.modules.payment.wechat.*;
import com.gas.ecommerce.modules.payment.mapper.PaymentMapper;
import com.gas.ecommerce.modules.payment.entity.Payment;
import com.gas.ecommerce.modules.payment.service.*;
import com.gas.ecommerce.modules.payment.dto.PaymentCallbackRequest;
import java.math.BigDecimal;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WechatNotifyTests {
    @Test void invalidSignatureNeverDecryptsOrChangesPayment() throws Exception {
        var client=mock(WechatClient.class); var payments=mock(PaymentMapper.class); var outcomes=mock(PaymentOutcomeHandler.class);
        @SuppressWarnings("unchecked") var provider=(ObjectProvider<WechatPayGateway>)mock(ObjectProvider.class);
        var controller=new WechatNotifyController(client,new WechatProperties(),new ObjectMapper(),payments,provider,outcomes);
        doThrow(new java.security.GeneralSecurityException()).when(client).verify(anyString(),anyString(),anyString(),anyString(),anyString());
        assertEquals(500,controller.notify("key","timestamp","nonce","forged","{}").getStatusCode().value());
        verifyNoInteractions(payments,provider,outcomes);
    }
    @Test void authenticatedNotificationReusesTransactionalOutcomeHandlerAndAcknowledgesOnlyAfterSuccess() throws Exception {
        var json=new ObjectMapper(); var client=mock(WechatClient.class); var payments=mock(PaymentMapper.class); var outcomes=mock(PaymentOutcomeHandler.class);
        @SuppressWarnings("unchecked") var provider=(ObjectProvider<WechatPayGateway>)mock(ObjectProvider.class);
        var adapter=mock(WechatPayGateway.class); when(provider.getIfAvailable()).thenReturn(adapter);
        var config=new WechatProperties(); config.setApiV3Key("01234567890123456789012345678901");
        String nonce="012345678901",aad="transaction",transaction="{\"out_trade_no\":\"PAY1\"}";
        var cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(config.getApiV3Key().getBytes(StandardCharsets.UTF_8),"AES"),new GCMParameterSpec(128,nonce.getBytes(StandardCharsets.UTF_8)));
        cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8)); String ciphertext=Base64.getEncoder().encodeToString(cipher.doFinal(transaction.getBytes(StandardCharsets.UTF_8)));
        var notification=json.createObjectNode().put("event_type","TRANSACTION.SUCCESS");
        notification.set("resource",json.createObjectNode().put("algorithm","AEAD_AES_256_GCM").put("nonce",nonce).put("associated_data",aad).put("ciphertext",ciphertext));
        var payment=new Payment(); payment.setChannel("wechat_mini");
        when(payments.selectOne(any())).thenReturn(payment);
        var receipt=new PaymentCallbackRequest("PAY1","T1",new BigDecimal("10.00"),"success",LocalDateTime.now());
        when(adapter.verifiedSnapshot(eq(payment),any())).thenReturn(new PaymentProviderGateway.Snapshot(PaymentProviderGateway.State.SUCCESS,receipt));
        var controller=new WechatNotifyController(client,config,json,payments,provider,outcomes);
        assertEquals(204,controller.notify("serial","time","nonce","signature",json.writeValueAsString(notification)).getStatusCode().value());
        verify(outcomes).accept("wechat_mini",receipt);
        doThrow(new IllegalStateException("database unavailable")).when(outcomes).accept("wechat_mini",receipt);
        assertEquals(500,controller.notify("serial","time","nonce","signature",json.writeValueAsString(notification)).getStatusCode().value());
    }
}
