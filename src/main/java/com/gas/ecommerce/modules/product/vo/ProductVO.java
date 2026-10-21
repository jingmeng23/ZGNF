package com.gas.ecommerce.modules.product.vo;
import java.math.BigDecimal;
public record ProductVO(Long id, String name, String subtitle, String coverUrl, BigDecimal price, BigDecimal originalPrice, Long sales) { }
