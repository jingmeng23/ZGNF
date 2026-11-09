package com.gas.ecommerce.modules.inventory.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
/**
 * 数据库实体，字段对应数据库表中的列。
 */
@Data @EqualsAndHashCode(callSuper = true) @TableName("inventory") public class Inventory extends BaseEntity { private Long productId;  private Integer quantity; private Integer lockedQuantity; @com.baomidou.mybatisplus.annotation.Version private Integer version; }
