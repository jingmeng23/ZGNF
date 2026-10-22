package com.gas.ecommerce.modules.order.vo;
import java.math.BigDecimal;
/**
 * 视图对象（VO），整理后返回给客户端展示的数据。
 */
public record OrderVO(Long id, String orderNo, String status, BigDecimal totalAmount, BigDecimal payAmount) { }
