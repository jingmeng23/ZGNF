package com.gas.ecommerce.modules.cart.vo;
import java.math.BigDecimal;
/**
 * 视图对象（VO），整理后返回给客户端展示的数据。
 */
public record CartItemVO(Long id, Long productId, String productName, Integer quantity, BigDecimal unitPrice, Boolean selected) { }
