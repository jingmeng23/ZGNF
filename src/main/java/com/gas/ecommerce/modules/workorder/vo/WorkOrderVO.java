package com.gas.ecommerce.modules.workorder.vo;
import com.gas.ecommerce.modules.order.vo.OrderAddressVO;
import java.time.LocalDateTime;
/**
 * 视图对象（VO），整理后返回给客户端展示的数据。
 */
public record WorkOrderVO(Long id,String workOrderNo,Long userId,Long employeeId,
        String title,String description,String type,String status,OrderAddressVO address,
        LocalDateTime appointmentTime,LocalDateTime acceptedTime,LocalDateTime startedTime,
        LocalDateTime completedTime,String result,LocalDateTime createTime,LocalDateTime updateTime) { }
