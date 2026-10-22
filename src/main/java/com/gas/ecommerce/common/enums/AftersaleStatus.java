package com.gas.ecommerce.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
/**
 * 业务状态枚举，集中管理代码常量及其数据库/接口字符串值。
 * 售后状态
 */

public enum AftersaleStatus {
    PENDING("pending"),//待处理
    APPROVED("approved"),//同意
    REJECTED("rejected"),//拒绝
    COMPLETED("completed");//完成

    @EnumValue private final String value;
    AftersaleStatus(String value) { this.value = value; }
    @JsonValue public String getValue() { return value; }
}