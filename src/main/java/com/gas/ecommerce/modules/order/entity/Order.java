package com.gas.ecommerce.modules.order.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
/**
 * 数据库实体，字段对应数据库表中的列。
 */
@Data @EqualsAndHashCode(callSuper = true) @TableName("`order`")
public class Order extends BaseEntity {
    private String orderNo;
    private Long userId;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private Long userCouponId;
    private BigDecimal discountAmount;
    private BigDecimal freightAmount;
    private BigDecimal fullReductionAmount;
    private BigDecimal couponDiscountAmount;
    private LocalDateTime expireTime;
    private String addressSnapshot;
    private String remark;
    private String requestId;
}
