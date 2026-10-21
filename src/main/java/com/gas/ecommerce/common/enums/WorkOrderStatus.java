package com.gas.ecommerce.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

public enum WorkOrderStatus {
    PENDING("pending"),
    ACCEPTED("accepted"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    CANCELLED("cancelled");

    @EnumValue private final String value;
    WorkOrderStatus(String value) { this.value = value; }
    @JsonValue public String getValue() { return value; }
}