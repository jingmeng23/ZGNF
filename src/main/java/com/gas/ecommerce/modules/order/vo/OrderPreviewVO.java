package com.gas.ecommerce.modules.order.vo;
import java.math.BigDecimal;
import java.util.List;
/** Informational quote; submission recalculates all amounts. */
public record OrderPreviewVO(String orderNo, String checkoutToken, java.time.LocalDateTime expireTime, OrderAddressVO address, List<OrderItemVO> items,
        BigDecimal totalAmount, BigDecimal discountAmount, BigDecimal payAmount,
        BigDecimal freightAmount, BigDecimal fullReductionAmount, BigDecimal couponDiscountAmount) { }
