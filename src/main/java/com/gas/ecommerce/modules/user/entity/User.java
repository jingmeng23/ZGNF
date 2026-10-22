package com.gas.ecommerce.modules.user.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("`user`")
/**
 * 数据库实体，字段对应数据库表中的列，并由 MyBatis-Plus 映射。
 */
public class User extends BaseEntity { private String phone; private String phoneHash; private String phoneLast4; private String nickname; private String avatar; private String status; }
