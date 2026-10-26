package com.gas.ecommerce.modules.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gas.ecommerce.modules.cart.entity.CartItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {
    @Select("SELECT id, user_id, product_id, quantity, selected, price_snapshot, product_name_snapshot, cover_snapshot, create_time, update_time, is_deleted " +
            "FROM cart_item WHERE user_id = #{userId} AND product_id = #{productId} AND is_deleted = 1 FOR UPDATE")
    CartItem selectDeleted(@Param("userId") Long userId, @Param("productId") Long productId);

    @Select("SELECT id, user_id, product_id, quantity, selected, price_snapshot, product_name_snapshot, cover_snapshot, create_time, update_time, is_deleted " +
            "FROM cart_item WHERE id = #{id} AND is_deleted = 0")
    CartItem selectActiveById(@Param("id") Long id);

    @Update("UPDATE cart_item SET quantity = #{quantity}, selected = 1, is_deleted = 0, " +
            "price_snapshot = #{price}, product_name_snapshot=#{name}, cover_snapshot=#{cover}, update_time = CURRENT_TIMESTAMP WHERE id = #{id} AND is_deleted = 1")
    int restore(@Param("id") Long id, @Param("quantity") Integer quantity, @Param("price") java.math.BigDecimal price,
            @Param("name") String name, @Param("cover") String cover);

    @Update("UPDATE cart_item SET price_snapshot = #{price} WHERE id = #{id} AND is_deleted = 0")
    int updatePriceSnapshot(@Param("id") Long id, @Param("price") java.math.BigDecimal price);
}
