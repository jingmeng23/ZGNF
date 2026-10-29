package com.gas.ecommerce.modules.aftersale.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AftersaleVO(Long id, String aftersaleNo, Long orderId, String orderNo,
        BigDecimal amount, String status, String reason, String reviewRemark,
        String refundReference, LocalDateTime createTime, LocalDateTime updateTime,
        Long reviewedBy, String completionRemark, LocalDateTime completedTime) { }
