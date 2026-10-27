package com.gas.ecommerce.modules.order.vo;
import java.math.BigDecimal;
public record OrderItemVO(Long productId, String productName, String productImage, Integer quantity,
        BigDecimal unitPrice, BigDecimal amount, BigDecimal discountAmount, BigDecimal payAmount) {
    public OrderItemVO(Long productId, String productName, String productImage, Integer quantity, BigDecimal unitPrice, BigDecimal amount) {
        this(productId, productName, productImage, quantity, unitPrice, amount, BigDecimal.ZERO, amount);
    }
}
