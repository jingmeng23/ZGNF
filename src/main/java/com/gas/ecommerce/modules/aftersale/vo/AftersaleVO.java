package com.gas.ecommerce.modules.aftersale.vo;
import java.math.BigDecimal;
public record AftersaleVO(Long id, String aftersaleNo, Long orderId, BigDecimal amount, String status) { }
