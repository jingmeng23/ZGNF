package com.gas.ecommerce.modules.order.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderVO(Long id, Long userId, String orderNo, String status,
        BigDecimal totalAmount, BigDecimal payAmount, Long userCouponId, BigDecimal discountAmount, String remark,
        LocalDateTime createTime, LocalDateTime expireTime,
        OrderAddressVO address, List<OrderItemVO> items,
        BigDecimal freightAmount, BigDecimal fullReductionAmount, BigDecimal couponDiscountAmount,
        List<OrderStatusLogVO> timeline,List<OrderPaymentVO> payments,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) OrderLogisticsVO logistics) {
    public OrderVO(Long id,Long userId,String orderNo,String status,BigDecimal totalAmount,BigDecimal payAmount,
            Long userCouponId,BigDecimal discountAmount,String remark,LocalDateTime createTime,LocalDateTime expireTime,
            OrderAddressVO address,List<OrderItemVO> items,BigDecimal freightAmount,BigDecimal fullReductionAmount,BigDecimal couponDiscountAmount) {
        this(id,userId,orderNo,status,totalAmount,payAmount,userCouponId,discountAmount,remark,createTime,expireTime,address,items,
                freightAmount,fullReductionAmount,couponDiscountAmount,List.of(),List.of(),null);
    }
    public OrderVO withDetail(List<OrderStatusLogVO> timeline,List<OrderPaymentVO> payments,OrderLogisticsVO logistics) {
        return new OrderVO(id,userId,orderNo,status,totalAmount,payAmount,userCouponId,discountAmount,remark,createTime,expireTime,address,items,
                freightAmount,fullReductionAmount,couponDiscountAmount,timeline,payments,logistics);
    }
}
