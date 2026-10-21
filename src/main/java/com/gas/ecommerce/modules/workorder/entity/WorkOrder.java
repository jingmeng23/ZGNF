package com.gas.ecommerce.modules.workorder.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("work_order") public class WorkOrder extends BaseEntity { private String workOrderNo; private Long userId; private Long storeId; private String type; private String status; private String title; private String description; }
