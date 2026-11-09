package com.gas.ecommerce.modules.workorder.service;
import com.baomidou.mybatisplus.spring.service.IService;
import com.gas.ecommerce.common.result.PageRequest;
import com.gas.ecommerce.common.result.PageResult;
import com.gas.ecommerce.modules.workorder.dto.WorkOrderActionRequest;
import com.gas.ecommerce.modules.workorder.dto.WorkOrderAssignRequest;
import com.gas.ecommerce.modules.workorder.dto.WorkOrderCreateRequest;
import com.gas.ecommerce.modules.workorder.dto.WorkOrderQuery;
import com.gas.ecommerce.modules.workorder.entity.WorkOrder;
import com.gas.ecommerce.modules.workorder.vo.WorkOrderLogVO;
import com.gas.ecommerce.modules.workorder.vo.WorkOrderVO;
// MyBatis-Plus 3.5.17 将 Spring Service 接口包从 extension.service 调整到了 spring.service。
/**
 * 业务服务接口，供 Controller 调用；继承 IService 后包含通用 CRUD 契约。
 */
public interface WorkOrderService extends IService<WorkOrder> {
    com.gas.ecommerce.modules.workorder.vo.WorkOrderOptionsVO options();
    PageResult<WorkOrderVO> listAssigned(Long employeeId,WorkOrderQuery q);
    WorkOrderVO detailAssigned(Long employeeId,Long id);
    PageResult<WorkOrderLogVO> logsAssigned(Long employeeId,Long id,PageRequest q);
    WorkOrderVO create(Long userId,WorkOrderCreateRequest r);
    PageResult<WorkOrderVO> listMine(Long userId,WorkOrderQuery q);
    PageResult<WorkOrderVO> listAdmin(WorkOrderQuery q);
    WorkOrderVO detail(Long userId,Long id);
    WorkOrderVO detailAdmin(Long id);
    PageResult<WorkOrderLogVO> logs(Long userId,Long id,PageRequest q);
    WorkOrderVO cancel(Long userId,Long id,WorkOrderActionRequest r);
    WorkOrderVO assign(Long operatorId,Long id,WorkOrderAssignRequest r);
    WorkOrderVO start(Long operatorId,Long id,WorkOrderActionRequest r);
    WorkOrderVO complete(Long operatorId,Long id,WorkOrderActionRequest r);
}
