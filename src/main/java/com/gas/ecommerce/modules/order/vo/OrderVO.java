package com.gas.ecommerce.modules.order.vo;
import java.math.BigDecimal;
public record OrderVO(Long id, String orderNo, String status, BigDecimal totalAmount, BigDecimal payAmount) { }
