package com.gas.ecommerce.modules.product.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("brand") public class Brand extends BaseEntity { private String name; private String logo; private String description; private String status; }
