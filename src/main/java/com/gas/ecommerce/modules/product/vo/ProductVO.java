package com.gas.ecommerce.modules.product.vo;
import java.math.BigDecimal;
/**
 * 视图对象（VO），整理后返回给客户端展示的数据。
 */
public record ProductVO(Long id, String name, String subtitle, String coverUrl, BigDecimal price, BigDecimal originalPrice, Long sales) { }
