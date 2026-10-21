package com.gas.ecommerce.modules.workorder.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("work_order_log") public class WorkOrderLog extends BaseEntity { private Long workOrderId; private Long operatorId; private String action; private String content; }
