package com.gas.ecommerce.modules.system.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("employee") public class Employee extends BaseEntity { private Long departmentId; private Long positionId; private String username; private String passwordHash; private String phone; private String phoneLast4; private String status; }
