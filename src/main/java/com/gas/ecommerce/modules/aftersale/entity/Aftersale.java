package com.gas.ecommerce.modules.aftersale.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("aftersale") public class Aftersale extends BaseEntity { private String aftersaleNo; private Long orderId; private Long userId; private String reason; private BigDecimal amount; private String status; }
