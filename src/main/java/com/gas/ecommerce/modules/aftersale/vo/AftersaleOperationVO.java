package com.gas.ecommerce.modules.aftersale.vo;

import java.time.LocalDateTime;

public record AftersaleOperationVO(Long id, String action, String fromStatus, String toStatus,
        Long operatorId, String operatorType, String remark, LocalDateTime occurredAt) { }
