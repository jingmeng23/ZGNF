package com.gas.ecommerce.modules.system.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("department") public class Department extends BaseEntity { private Long parentId; private String name; private String status; }
