package com.gas.ecommerce.modules.aftersale.vo;
import java.math.BigDecimal;
/**
 * 视图对象（VO），整理后返回给客户端展示的数据。
 */
public record AftersaleVO(Long id, String aftersaleNo, Long orderId, BigDecimal amount, String status) { }
