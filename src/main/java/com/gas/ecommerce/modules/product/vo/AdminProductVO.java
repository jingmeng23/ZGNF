package com.gas.ecommerce.modules.product.vo;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record AdminProductVO(Long id, Long categoryId, String category, Long brandId,
        String name, String subtitle, String coverUrl, BigDecimal price, BigDecimal originalPrice,
        String status, Long sales, Long stock, LocalDateTime createTime, LocalDateTime updateTime) { }
