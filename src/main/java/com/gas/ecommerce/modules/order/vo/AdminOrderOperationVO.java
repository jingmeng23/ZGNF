package com.gas.ecommerce.modules.order.vo;

import java.time.LocalDateTime;

public record AdminOrderOperationVO(Long id, Long operatorId, String action, String remark, LocalDateTime occurredAt) { }
