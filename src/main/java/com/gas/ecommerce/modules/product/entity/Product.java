package com.gas.ecommerce.modules.product.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("product")
public class Product extends BaseEntity { private Long categoryId; private Long brandId; private String name; private String subtitle; private String coverUrl; private BigDecimal price; private BigDecimal originalPrice; private String status; private Long sales; }
