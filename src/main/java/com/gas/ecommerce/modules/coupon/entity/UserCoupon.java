package com.gas.ecommerce.modules.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper=true) @TableName("user_coupon")
public class UserCoupon extends BaseEntity {
    private Long userId;
    private Long couponId;
    private String name;
    private BigDecimal amount;
    private BigDecimal minimumAmount;
    private LocalDateTime startTime;
    private LocalDateTime expireTime;
    private String status;
    private Long usedOrderId;
}
