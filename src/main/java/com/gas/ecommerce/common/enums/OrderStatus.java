package com.gas.ecommerce.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OrderStatus {
    PENDING_PAYMENT("pending_payment"),
    PAID("paid"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    CANCELLED("cancelled"),
    REFUNDED("refunded");

    @EnumValue private final String value;
    OrderStatus(String value) { this.value = value; }
    @JsonValue public String getValue() { return value; }
}