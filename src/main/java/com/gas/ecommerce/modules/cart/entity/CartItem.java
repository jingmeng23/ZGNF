package com.gas.ecommerce.modules.cart.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("cart_item") public class CartItem extends BaseEntity { private Long userId; private Long productId; private Integer quantity; private Boolean selected; }
