package com.gas.ecommerce.modules.coupon.dto;

import com.gas.ecommerce.common.result.PageRequest;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper=true)
public class UserCouponQuery extends PageRequest {
    @Pattern(regexp="unused|locked|used|expired") private String status;
    /** 按服务器订单金额再次校验；此字段只用于可用券筛选。 */
    @DecimalMin("0.00") @Digits(integer=8,fraction=2) private BigDecimal orderAmount;
}
