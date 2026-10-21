package com.gas.ecommerce.modules.product.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("category") public class Category extends BaseEntity { private Long parentId; private String name; private Integer sort; private String status; }
