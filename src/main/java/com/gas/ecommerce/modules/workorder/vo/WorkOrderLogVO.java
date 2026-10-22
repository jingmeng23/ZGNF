package com.gas.ecommerce.modules.workorder.vo;
import java.time.LocalDateTime;
public record WorkOrderLogVO(Long id,Long operatorId,String operatorType,String action,String content,LocalDateTime createTime) { }
