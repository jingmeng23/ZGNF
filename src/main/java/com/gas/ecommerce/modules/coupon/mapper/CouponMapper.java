package com.gas.ecommerce.modules.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gas.ecommerce.modules.system.entity.Coupon;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {
    @Select("SELECT * FROM coupon WHERE id=#{id} AND is_deleted=0 FOR UPDATE")
    Coupon lockById(@Param("id") Long id);
}
