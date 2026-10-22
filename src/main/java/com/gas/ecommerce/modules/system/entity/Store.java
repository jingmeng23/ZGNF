package com.gas.ecommerce.modules.system.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
/**
 * 数据库实体，字段对应数据库表中的列。
 */
@Data @EqualsAndHashCode(callSuper = true) @TableName("store") public class Store extends BaseEntity {
    private String name;
    private String phone;
    private String phoneLast4;
    private String province;
    private String city;
    private String district;
    private String address;
    private String manager;
    private String status;
}
