package com.gas.ecommerce.modules.coupon.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserCouponVO(Long id,Long couponId,String name,BigDecimal amount,BigDecimal minimumAmount,
        LocalDateTime startTime,LocalDateTime expireTime,String status,Long usedOrderId,LocalDateTime createTime) { }
