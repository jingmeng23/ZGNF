package com.gas.ecommerce.modules.order.vo;

import java.math.BigDecimal;

public record OrderItemVO(Long productId, String productName, String productImage,
        Integer quantity, BigDecimal unitPrice, BigDecimal amount) { }
