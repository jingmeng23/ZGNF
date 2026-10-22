package com.gas.ecommerce.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
/**
 * 业务状态枚举，集中管理代码常量及其数据库/接口字符串值。
 * 工单状态
 */

public enum WorkOrderStatus {
    PENDING("pending"),//待处理
    ACCEPTED("accepted"),//已接受
    PROCESSING("processing"),//处理中
    COMPLETED("completed"),//以完成
    CANCELLED("cancelled");//已取消

    @EnumValue private final String value;
    WorkOrderStatus(String value) { this.value = value; }
    @JsonValue public String getValue() { return value; }
}