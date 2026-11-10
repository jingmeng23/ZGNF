package com.gas.ecommerce.modules.payment.wechat;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Secrets belong to the server environment, never the mini-program bundle. */
@Component
@ConfigurationProperties(prefix = "app.wechat")
public class WechatProperties {
    private boolean enabled;
    private String appId = "", appSecret = "", merchantId = "", merchantSerial = "",
            privateKeyPath = "", platformPublicKeyPath = "", platformSerial = "", apiV3Key = "", notifyUrl = "";
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled=v; }
    public String getAppId() { return appId; }
    public void setAppId(String v) { appId=v; }
    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String v) { appSecret=v; }
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String v) { merchantId=v; }
    public String getMerchantSerial() { return merchantSerial; }
    public void setMerchantSerial(String v) { merchantSerial=v; }
    public String getPrivateKeyPath() { return privateKeyPath; }
    public void setPrivateKeyPath(String v) { privateKeyPath=v; }
    public String getPlatformPublicKeyPath() { return platformPublicKeyPath; }
    public void setPlatformPublicKeyPath(String v) { platformPublicKeyPath=v; }
    public String getPlatformSerial() { return platformSerial; }
    public void setPlatformSerial(String v) { platformSerial=v; }
    public String getApiV3Key() { return apiV3Key; }
    public void setApiV3Key(String v) { apiV3Key=v; }
    public String getNotifyUrl() { return notifyUrl; }
    public void setNotifyUrl(String v) { notifyUrl=v; }
}
