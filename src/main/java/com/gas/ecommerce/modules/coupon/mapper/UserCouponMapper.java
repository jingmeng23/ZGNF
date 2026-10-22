package com.gas.ecommerce.modules.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gas.ecommerce.modules.coupon.entity.UserCoupon;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {
    @Select("SELECT * FROM user_coupon WHERE id=#{id} AND is_deleted=0 FOR UPDATE")
    UserCoupon lockById(@Param("id") Long id);
    @Select("SELECT COUNT(*) FROM user_coupon WHERE coupon_id=#{id}")
    long claimedCount(@Param("id") Long id);
}
