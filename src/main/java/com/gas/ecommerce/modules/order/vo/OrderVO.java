package com.gas.ecommerce.modules.order.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderVO(Long id, Long userId, String orderNo, String status,
        BigDecimal totalAmount, BigDecimal payAmount, Long userCouponId, BigDecimal discountAmount, String remark,
        LocalDateTime createTime, LocalDateTime expireTime,
        OrderAddressVO address, List<OrderItemVO> items) { }
