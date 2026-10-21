package com.gas.ecommerce.modules.system.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("role_permission") public class RolePermission extends BaseEntity { private Long roleId; private Long permissionId; }
