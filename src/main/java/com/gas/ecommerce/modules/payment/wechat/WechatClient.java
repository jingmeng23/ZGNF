package com.gas.ecommerce.modules.payment.wechat;

import com.fasterxml.jackson.databind.*;
import com.gas.ecommerce.common.exception.BusinessException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class WechatClient {
    private final WechatProperties config;
    private final ObjectMapper json;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public WechatClient(WechatProperties config,ObjectMapper json) { this.config=config; this.json=json; }
    public static class ProviderException extends BusinessException {
        private final String providerCode;
        public ProviderException(String code) { super("微信支付请求未成功，请查询订单后重试"); this.providerCode=code; }
        public String providerCode() { return providerCode; }
    }
    public void requireEnabled() {
        if (!config.isEnabled()) throw new BusinessException("微信小程序支付尚未配置");
    }
    public String openId(String code) {
        requireEnabled();
        if (config.getAppId().isBlank() || config.getAppSecret().isBlank()) throw new BusinessException("微信登录尚未配置");
        try {
            String url="https://api.weixin.qq.com/sns/jscode2session?appid="+encode(config.getAppId())
                    +"&secret="+encode(config.getAppSecret())+"&js_code="+encode(code)+"&grant_type=authorization_code";
            var response=http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.ofString());
            JsonNode body=json.readTree(response.body());
            if (response.statusCode()!=200 || !body.hasNonNull("openid") || body.path("errcode").asInt()!=0)
                throw new BusinessException("微信登录凭证无效或已过期，请重试");
            return body.get("openid").asText();
        } catch (BusinessException ex) { throw ex; }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new BusinessException("微信服务请求中断"); }
        catch (Exception ex) { throw new BusinessException("暂时无法连接微信登录服务"); }
    }
    public JsonNode request(String method,String path,Object payload) {
        requireEnabled();
        try {
            String body=payload==null?"":json.writeValueAsString(payload);
            String timestamp=String.valueOf(Instant.now().getEpochSecond()),nonce=UUID.randomUUID().toString().replace("-", "");
            String signature=WechatCrypto.sign(WechatCrypto.privateKey(config.getPrivateKeyPath()),method+"\n"+path+"\n"+timestamp+"\n"+nonce+"\n"+body+"\n");
            String auth="WECHATPAY2-SHA256-RSA2048 mchid=\""+config.getMerchantId()+"\",nonce_str=\""+nonce
                    +"\",timestamp=\""+timestamp+"\",serial_no=\""+config.getMerchantSerial()+"\",signature=\""+signature+"\"";
            var builder=HttpRequest.newBuilder(URI.create("https://api.mch.weixin.qq.com"+path)).timeout(Duration.ofSeconds(5))
                    .header("Authorization",auth).header("Accept","application/json").header("Content-Type","application/json")
                    .header("Wechatpay-Serial",config.getPlatformSerial());
            var response=http.send(builder.method(method,body.isEmpty()?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
            // Never trust provider data or error codes without verifying the response signature.
            verify(response.headers().firstValue("Wechatpay-Serial").orElse(""),response.headers().firstValue("Wechatpay-Timestamp").orElse(""),
                    response.headers().firstValue("Wechatpay-Nonce").orElse(""),response.headers().firstValue("Wechatpay-Signature").orElse(""),response.body());
            if (response.statusCode()<200 || response.statusCode()>=300) throw new ProviderException(json.readTree(response.body()).path("code").asText());
            return response.body().isBlank()?json.createObjectNode():json.readTree(response.body());
        } catch (BusinessException ex) { throw ex; }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new BusinessException("支付请求中断，请查询订单结果"); }
        catch (Exception ex) { throw new BusinessException("微信支付通信或签名校验失败，请查询订单结果"); }
    }
    public void verify(String serial,String timestamp,String nonce,String signature,String body) throws Exception {
        requireEnabled();
        if (config.getPlatformSerial().isBlank() || !config.getPlatformSerial().equals(serial))
            throw new GeneralSecurityException("Untrusted WeChat Pay public key ID");
        WechatCrypto.verify(WechatCrypto.publicKey(config.getPlatformPublicKeyPath()),timestamp,nonce,signature,body);
    }
    public Map<String,String> paymentParameters(String prepayId) {
        try {
            String timestamp=String.valueOf(Instant.now().getEpochSecond()),nonce=UUID.randomUUID().toString().replace("-", ""),pack="prepay_id="+prepayId;
            String signature=WechatCrypto.sign(WechatCrypto.privateKey(config.getPrivateKeyPath()),config.getAppId()+"\n"+timestamp+"\n"+nonce+"\n"+pack+"\n");
            return Map.of("timeStamp",timestamp,"nonceStr",nonce,"package",pack,"signType","RSA","paySign",signature);
        } catch (Exception ex) { throw new BusinessException("支付签名配置错误"); }
    }
    public static String encode(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8); }
}
