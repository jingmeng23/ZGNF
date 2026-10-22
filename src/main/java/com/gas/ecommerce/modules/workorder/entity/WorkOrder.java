package com.gas.ecommerce.modules.workorder.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gas.ecommerce.common.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
/**
 * 数据库实体，字段对应数据库表中的列。
 */
@Data @EqualsAndHashCode(callSuper = true) @TableName("work_order")
public class WorkOrder extends BaseEntity {
    private String workOrderNo;
    private Long userId;
    private Long storeId;
    private Long employeeId;
    private String requestId;
    private String type;
    private String status;
    private String title;
    private String description;
    private String addressSnapshot;
    private LocalDateTime appointmentTime;
    private LocalDateTime acceptedTime;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;
    private String result;
}
