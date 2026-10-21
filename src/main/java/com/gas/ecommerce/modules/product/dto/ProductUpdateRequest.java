package com.gas.ecommerce.modules.product.dto;
import java.math.BigDecimal;
public record ProductUpdateRequest(String name, String subtitle, BigDecimal price, String status) { }
