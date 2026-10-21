package com.gas.ecommerce.modules.system.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("operation_log") public class OperationLog extends BaseEntity { private Long operatorId; private String operation; private String requestUri; private String requestMethod; private String traceId; private Long durationMs; private Boolean success; }
