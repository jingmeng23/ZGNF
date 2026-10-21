package com.gas.ecommerce.modules.payment.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("payment") public class Payment extends BaseEntity { private String paymentNo; private Long orderId; private Long userId; private BigDecimal amount; private String channel; private String status; private LocalDateTime paidTime; }
