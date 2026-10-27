package com.gas.ecommerce.modules.order.vo;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/** User projection deliberately excludes provider transaction identity and credentials. */
public record OrderPaymentVO(String paymentNo,String channel,String status,BigDecimal amount,LocalDateTime paidTime) { }
