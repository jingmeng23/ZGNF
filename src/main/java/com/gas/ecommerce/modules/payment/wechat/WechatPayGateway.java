package com.gas.ecommerce.modules.payment.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.modules.payment.dto.PaymentCallbackRequest;
import com.gas.ecommerce.modules.payment.entity.Payment;
import com.gas.ecommerce.modules.payment.service.PaymentProviderGateway;
import java.math.BigDecimal;
import java.time.*;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="app.wechat.enabled",havingValue="true")
public class WechatPayGateway implements PaymentProviderGateway {
    public static final String CHANNEL="wechat_mini";
    private final WechatClient client;
    private final WechatProperties config;
    private final WechatIdentityService identities;
    public WechatPayGateway(WechatClient client,WechatProperties config,WechatIdentityService identities) {
        this.client=client; this.config=config; this.identities=identities;
    }
    public static int fen(BigDecimal amount) {
        int value=amount.movePointRight(2).intValueExact();
        if(value<=0) throw new BusinessException("微信支付金额必须大于零");
        return value;
    }
    public Map<String,String> prepay(Payment p,LocalDateTime expireTime) {
        if(!CHANNEL.equals(p.getChannel()) || !"pending".equals(p.getStatus())) throw new BusinessException("支付单状态不支持微信预支付");
        if(!config.getNotifyUrl().startsWith("https://")) throw new BusinessException("微信支付回调必须配置 HTTPS 地址");
        var body=Map.of("appid",config.getAppId(),"mchid",config.getMerchantId(),"description","自贡能发商城订单",
                "out_trade_no",p.getPaymentNo(),"notify_url",config.getNotifyUrl(),
                "time_expire",expireTime.atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime().toString(),
                "amount",Map.of("total",fen(p.getAmount()),"currency","CNY"),"payer",Map.of("openid",identities.openId(p.getUserId())));
        var result=client.request("POST","/v3/pay/transactions/jsapi",body);
        if(result.path("prepay_id").asText().isBlank()) throw new BusinessException("微信未返回预支付凭证");
        return client.paymentParameters(result.get("prepay_id").asText());
    }
    public Snapshot verifiedSnapshot(Payment p,JsonNode result) {
        if(!config.getAppId().equals(result.path("appid").asText()) || !config.getMerchantId().equals(result.path("mchid").asText())
                || !p.getPaymentNo().equals(result.path("out_trade_no").asText())
                || !"CNY".equals(result.path("amount").path("currency").asText())
                || !result.path("amount").path("total").isIntegralNumber()
                || result.path("amount").path("total").asLong()!=fen(p.getAmount())
                || !identities.paymentOpenId(p.getUserId()).equals(result.path("payer").path("openid").asText()))
            throw new BusinessException("微信支付商户、付款人或金额不匹配");
        return switch(result.path("trade_state").asText()) {
            case "SUCCESS" -> {
                String trade=result.path("transaction_id").asText();
                if(trade.isBlank()) throw new BusinessException("微信交易流水缺失");
                var paid=OffsetDateTime.parse(result.path("success_time").asText()).atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDateTime();
                yield new Snapshot(State.SUCCESS,new PaymentCallbackRequest(p.getPaymentNo(),trade,p.getAmount(),"success",paid));
            }
            case "CLOSED", "REVOKED" -> new Snapshot(State.CLOSED,null);
            case "NOTPAY", "USERPAYING", "PAYERROR" -> new Snapshot(State.PENDING,null);
            default -> Snapshot.unknown();
        };
    }
    @Override public Snapshot query(Payment p) {
        if(!CHANNEL.equals(p.getChannel())) return Snapshot.unknown();
        return verifiedSnapshot(p,client.request("GET","/v3/pay/transactions/out-trade-no/"+WechatClient.encode(p.getPaymentNo())+"?mchid="+WechatClient.encode(config.getMerchantId()),null));
    }
    @Override public Snapshot close(Payment p) {
        if(!CHANNEL.equals(p.getChannel())) return Snapshot.unknown();
        // A failed close request cannot establish closure. Query only supplies a verified success/closed outcome.
        try {
            client.request("POST","/v3/pay/transactions/out-trade-no/"+WechatClient.encode(p.getPaymentNo())+"/close",Map.of("mchid",config.getMerchantId()));
            return new Snapshot(State.CLOSED,null);
        } catch(WechatClient.ProviderException ex) {
            // Signed ORDER_NOT_EXIST + the caller's order lock fence any future prepay for this order.
            if ("ORDER_NOT_EXIST".equals(ex.providerCode())) return new Snapshot(State.CLOSED,null);
            return query(p);
        } catch(BusinessException ex) { return query(p); }
    }
    @Override public boolean refund(Payment p,String refundNo) {
        if(!CHANNEL.equals(p.getChannel())) return false;
        JsonNode result;
        try { result=client.request("GET","/v3/refund/domestic/refunds/"+WechatClient.encode(refundNo),null); }
        catch(WechatClient.ProviderException ex) {
            if (!"RESOURCE_NOT_EXISTS".equals(ex.providerCode())) throw ex;
            result=client.request("POST","/v3/refund/domestic/refunds",Map.of("out_trade_no",p.getPaymentNo(),"out_refund_no",refundNo,
                    "reason","商城订单退款","amount",Map.of("refund",fen(p.getAmount()),"total",fen(p.getAmount()),"currency","CNY")));
        }
        return "SUCCESS".equals(result.path("status").asText()) && refundNo.equals(result.path("out_refund_no").asText())
                && p.getPaymentNo().equals(result.path("out_trade_no").asText())
                && "CNY".equals(result.path("amount").path("currency").asText())
                && result.path("amount").path("refund").asLong(-1)==fen(p.getAmount())
                && result.path("amount").path("total").asLong(-1)==fen(p.getAmount());
    }
}
