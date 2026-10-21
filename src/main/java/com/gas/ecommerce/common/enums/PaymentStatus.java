package com.gas.ecommerce.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentStatus {
    PENDING("pending"),
    SUCCESS("success"),
    FAILED("failed"),
    REFUNDED("refunded");

    @EnumValue private final String value;
    PaymentStatus(String value) { this.value = value; }
    @JsonValue public String getValue() { return value; }
}