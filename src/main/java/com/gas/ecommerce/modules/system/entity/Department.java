package com.gas.ecommerce.modules.system.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
/**
 * 数据库实体，字段对应数据库表中的列。
 */
@Data @EqualsAndHashCode(callSuper = true) @TableName("department") public class Department extends BaseEntity { private Long parentId; private String name; private String status; }
