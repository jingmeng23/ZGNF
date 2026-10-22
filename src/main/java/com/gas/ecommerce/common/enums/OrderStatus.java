package com.gas.ecommerce.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
/**
 * 业务状态枚举，集中管理代码常量及其数据库/接口字符串值。
 * 点单状态
 */

public enum OrderStatus {
    PENDING_PAYMENT("pending_payment"),//待支付
    PAID("paid"),//已支付
    PROCESSING("processing"),//处理中
    COMPLETED("completed"),//已完成
    CANCELLED("cancelled"),//已取消
    REFUNDED("refunded");//已退款

    @EnumValue private final String value;
    OrderStatus(String value) { this.value = value; }
    @JsonValue public String getValue() { return value; }
}