package com.gas.ecommerce.modules.payment.vo;
import java.math.BigDecimal;
public record PaymentVO(Long id, String paymentNo, Long orderId, BigDecimal amount, String status) { }
