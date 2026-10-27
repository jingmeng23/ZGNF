package com.gas.ecommerce.modules.order.vo;
import java.time.LocalDateTime;
public record OrderStatusLogVO(Long id,String fromStatus,String toStatus,LocalDateTime occurredAt) { }
