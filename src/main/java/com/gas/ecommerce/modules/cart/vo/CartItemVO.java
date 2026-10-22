package com.gas.ecommerce.modules.cart.vo;

import java.math.BigDecimal;

public record CartItemVO(Long id, Long productId, String productName, String coverUrl,
        Integer quantity, BigDecimal unitPrice, long availableStock, Boolean selected,
        boolean available, BigDecimal subtotal) { }
