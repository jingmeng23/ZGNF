package com.gas.ecommerce.modules.cart.vo;
import java.math.BigDecimal;
public record CartItemVO(Long id, Long productId, String productName, Integer quantity, BigDecimal unitPrice, Boolean selected) { }
