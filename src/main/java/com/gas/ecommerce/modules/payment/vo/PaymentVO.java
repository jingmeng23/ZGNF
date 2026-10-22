package com.gas.ecommerce.modules.payment.vo;
import java.math.BigDecimal;
/**
 * 视图对象（VO），整理后返回给客户端展示的数据。
 */
public record PaymentVO(Long id, String paymentNo, Long orderId, BigDecimal amount, String status) { }
