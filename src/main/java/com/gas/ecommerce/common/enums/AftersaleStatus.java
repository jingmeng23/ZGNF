package com.gas.ecommerce.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AftersaleStatus {
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected"),
    COMPLETED("completed");

    @EnumValue private final String value;
    AftersaleStatus(String value) { this.value = value; }
    @JsonValue public String getValue() { return value; }
}