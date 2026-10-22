package com.gas.ecommerce.modules.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gas.ecommerce.modules.cart.entity.CartItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {
    @Select("SELECT id, user_id, product_id, quantity, selected, create_time, update_time, is_deleted " +
            "FROM cart_item WHERE user_id = #{userId} AND product_id = #{productId} AND is_deleted = 1 FOR UPDATE")
    CartItem selectDeleted(@Param("userId") Long userId, @Param("productId") Long productId);

    @Update("UPDATE cart_item SET quantity = #{quantity}, selected = 1, is_deleted = 0, " +
            "update_time = CURRENT_TIMESTAMP WHERE id = #{id} AND is_deleted = 1")
    int restore(@Param("id") Long id, @Param("quantity") Integer quantity);
}
