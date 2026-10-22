package com.gas.ecommerce.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
/**
 * 业务状态枚举，集中管理代码常量及其数据库/接口字符串值。
 * 支付状态
 */

public enum PaymentStatus {
    PENDING("pending"),//待处理
    SUCCESS("success"),//成功
    FAILED("failed"),//失败
    REFUNDED("refunded");//已退款

    @EnumValue private final String value;
    PaymentStatus(String value) { this.value = value; }
    @JsonValue public String getValue() { return value; }
}