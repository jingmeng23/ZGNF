package com.gas.ecommerce.modules.payment.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentVO(Long id, String paymentNo, Long orderId, BigDecimal amount,
        String channel, String status, LocalDateTime paidTime) { }
