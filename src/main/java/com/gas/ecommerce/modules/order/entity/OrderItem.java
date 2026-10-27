package com.gas.ecommerce.modules.order.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;
/**
 * 数据库实体，字段对应数据库表中的列。
 */
@Data @EqualsAndHashCode(callSuper = true) @TableName("order_item") public class OrderItem extends BaseEntity { private Long orderId; private Long productId; private String productName; private String productImage; private Integer quantity; private BigDecimal unitPrice; private BigDecimal amount; private BigDecimal discountAmount; private BigDecimal payAmount; }
