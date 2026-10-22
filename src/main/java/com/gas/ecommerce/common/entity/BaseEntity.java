package com.gas.ecommerce.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 多数数据库实体的公共父类，集中定义主键、创建/更新时间和软删除标记。
 * 继承本类后，MyBatis-Plus 会按这些注解自动处理对应的数据库字段。
 */
@Data
public class BaseEntity {
    /** 数据库自增主键；插入记录后 MyBatis-Plus 会把生成的值回填到此字段。 */
    @TableId(type = IdType.AUTO) private Long id;
    /** 插入记录时由自动填充处理器写入创建时间。 */
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    /** 插入或更新记录时由自动填充处理器写入最后更新时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    /** 软删除标记：0 为正常，1 为已删除；逻辑删除通常只更新此字段。 */
    @TableLogic private Integer isDeleted;
}
