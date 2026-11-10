package com.gas.ecommerce.modules.payment.wechat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.ecommerce.modules.payment.entity.Payment;
import com.gas.ecommerce.modules.payment.mapper.PaymentMapper;
import com.gas.ecommerce.modules.payment.service.*;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Public ONLY for WeChat's verified API v3 notification protocol, never our internal HMAC protocol. */
@RestController
public class WechatNotifyController {
    private final WechatClient client;
    private final WechatProperties config;
    private final ObjectMapper json;
    private final PaymentMapper payments;
    private final ObjectProvider<WechatPayGateway> gateway;
    private final PaymentOutcomeHandler outcomes;
    public WechatNotifyController(WechatClient client,WechatProperties config,ObjectMapper json,PaymentMapper payments,ObjectProvider<WechatPayGateway> gateway,PaymentOutcomeHandler outcomes) {
        this.client=client; this.config=config; this.json=json; this.payments=payments; this.gateway=gateway; this.outcomes=outcomes;
    }
    @PostMapping("/api/v1/payment/wechat/notify")
    public ResponseEntity<?> notify(@RequestHeader(value="Wechatpay-Serial",defaultValue="") String serial,
            @RequestHeader(value="Wechatpay-Timestamp",defaultValue="") String timestamp,
            @RequestHeader(value="Wechatpay-Nonce",defaultValue="") String nonce,
            @RequestHeader(value="Wechatpay-Signature",defaultValue="") String signature,@RequestBody String body) {
        try {
            if(body.length()>65536) return ResponseEntity.badRequest().body(Map.of("code","FAIL","message","通知过大"));
            client.verify(serial,timestamp,nonce,signature,body);
            var notification=json.readTree(body);
            if(!"TRANSACTION.SUCCESS".equals(notification.path("event_type").asText())) throw new IllegalArgumentException();
            var resource=notification.path("resource");
            if(!"AEAD_AES_256_GCM".equals(resource.path("algorithm").asText())) throw new IllegalArgumentException();
            var transaction=json.readTree(WechatCrypto.decrypt(config.getApiV3Key(),resource.path("nonce").asText(),
                    resource.path("associated_data").asText(),resource.path("ciphertext").asText()));
            var payment=payments.selectOne(Wrappers.<Payment>lambdaQuery().eq(Payment::getPaymentNo,transaction.path("out_trade_no").asText()));
            if(payment==null || !WechatPayGateway.CHANNEL.equals(payment.getChannel())) throw new IllegalArgumentException();
            var adapter=gateway.getIfAvailable();
            if(adapter==null) throw new IllegalStateException();
            var snapshot=adapter.verifiedSnapshot(payment,transaction);
            if(snapshot.state()!=PaymentProviderGateway.State.SUCCESS) throw new IllegalArgumentException();
            outcomes.accept(WechatPayGateway.CHANNEL,snapshot.outcome());
            return ResponseEntity.noContent().build();
        } catch(Exception ex) {
            // No plaintext, signatures, openid or key material in logs or responses. Non-2xx makes WeChat retry.
            return ResponseEntity.status(500).body(Map.of("code","FAIL","message","通知未确认，请重试"));
        }
    }
}
