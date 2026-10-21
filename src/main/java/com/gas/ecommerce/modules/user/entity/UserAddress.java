package com.gas.ecommerce.modules.user.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("user_address")
public class UserAddress extends BaseEntity { private Long userId; private String receiver; private String phone; private String phoneLast4; private String province; private String city; private String district; private String detail; private Boolean isDefault; }
