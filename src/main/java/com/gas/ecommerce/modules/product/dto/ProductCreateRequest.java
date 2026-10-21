package com.gas.ecommerce.modules.product.dto;
import java.math.BigDecimal;
public record ProductCreateRequest(Long categoryId, Long brandId, String name, BigDecimal price) { }
