package com.gas.ecommerce.modules.inventory.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
/**
 * 数据库实体，字段对应数据库表中的列。
 */
@Data @EqualsAndHashCode(callSuper = true) @TableName("inventory_log")
public class InventoryLog extends BaseEntity {
    private Long productId;  private Integer changeQuantity;
    private String changeType; private String referenceNo; private String remark;
    private Long inventoryId; private Long operatorId; private String requestId;
    private Integer requestedQuantity; private Integer quantityBefore; private Integer quantityAfter;
    private Integer lockedBefore; private Integer lockedAfter;
}
