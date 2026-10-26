package com.gas.ecommerce.modules.cart.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.gas.ecommerce.common.entity.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cart_item")
public class CartItem extends BaseEntity {
    private Long userId;
    private Long productId;
    private Integer quantity;
    private Boolean selected;
    @TableField("price_snapshot")
    private BigDecimal priceSnapshot;
    @TableField("product_name_snapshot")
    private String productNameSnapshot;
    @TableField("cover_snapshot")
    private String coverSnapshot;
}
