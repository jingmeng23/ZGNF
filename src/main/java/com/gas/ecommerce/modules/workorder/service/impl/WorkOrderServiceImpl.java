package com.gas.ecommerce.modules.workorder.service.impl;
// MyBatis-Plus 3.5.17 将 Spring Service 接口包从 extension.service 调整到了 spring.service。
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.gas.ecommerce.modules.workorder.entity.WorkOrder;
import com.gas.ecommerce.modules.workorder.mapper.WorkOrderMapper;
import com.gas.ecommerce.modules.workorder.service.WorkOrderService;
/**
 * 业务服务实现类；当前主要继承 MyBatis-Plus 通用 CRUD，复杂业务流程仍需补充。
 */
@org.springframework.stereotype.Service public class WorkOrderServiceImpl extends ServiceImpl<WorkOrderMapper, WorkOrder> implements WorkOrderService { }
