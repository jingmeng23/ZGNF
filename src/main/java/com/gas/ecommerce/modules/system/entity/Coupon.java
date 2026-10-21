package com.gas.ecommerce.modules.system.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("coupon") public class Coupon extends BaseEntity { private String name; private BigDecimal amount; private BigDecimal minimumAmount; private LocalDateTime startTime; private LocalDateTime endTime; private Integer quantity; private String status; }
