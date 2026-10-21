package com.gas.ecommerce.modules.inventory.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("inventory_log") public class InventoryLog extends BaseEntity { private Long productId; private Long storeId; private Integer changeQuantity; private String changeType; private String referenceNo; private String remark; }
