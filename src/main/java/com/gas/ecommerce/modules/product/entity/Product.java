package com.gas.ecommerce.modules.product.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("product")
/**
 * 数据库实体，字段对应数据库表中的列，并由 MyBatis-Plus 映射。
 */
public class Product extends BaseEntity { private Long categoryId; private Long brandId; private String name; private String subtitle; private String coverUrl; private BigDecimal price; private BigDecimal originalPrice; private String status; private Long sales; private String description; private String detailImages; }
