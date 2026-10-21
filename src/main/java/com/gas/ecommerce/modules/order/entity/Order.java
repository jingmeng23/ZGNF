package com.gas.ecommerce.modules.order.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("`order`") public class Order extends BaseEntity { private String orderNo; private Long userId; private String status; private BigDecimal totalAmount; private BigDecimal payAmount; private LocalDateTime expireTime; }
