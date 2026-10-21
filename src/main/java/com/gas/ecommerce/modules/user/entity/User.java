package com.gas.ecommerce.modules.user.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("user")
public class User extends BaseEntity { private String phone; private String phoneHash; private String phoneLast4; private String nickname; private String avatar; private String status; }
