package com.gas.ecommerce.modules.workorder.service.impl;
// MyBatis-Plus 3.5.17 moved Spring Service APIs from extension.service to spring.service.
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.gas.ecommerce.modules.workorder.entity.WorkOrder;
import com.gas.ecommerce.modules.workorder.mapper.WorkOrderMapper;
import com.gas.ecommerce.modules.workorder.service.WorkOrderService;
@org.springframework.stereotype.Service public class WorkOrderServiceImpl extends ServiceImpl<WorkOrderMapper, WorkOrder> implements WorkOrderService { }
