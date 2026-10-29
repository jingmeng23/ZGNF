package com.gas.ecommerce.modules.inventory.service;
// MyBatis-Plus 3.5.17 将 Spring Service 接口包从 extension.service 调整到了 spring.service。
import com.baomidou.mybatisplus.spring.service.IService;
import com.gas.ecommerce.modules.inventory.entity.Inventory;
/**
 * 业务服务接口，供 Controller 调用；继承 IService 后包含通用 CRUD 契约。
 */
public interface InventoryService extends IService<Inventory> {
    com.gas.ecommerce.modules.inventory.vo.InventoryOptionsVO options();
    com.gas.ecommerce.common.result.PageResult<com.gas.ecommerce.modules.inventory.vo.InventoryVO> list(com.gas.ecommerce.modules.inventory.dto.InventoryQuery q);
    com.gas.ecommerce.modules.inventory.vo.InventoryVO detail(Long id);
    com.gas.ecommerce.common.result.PageResult<com.gas.ecommerce.modules.inventory.vo.InventoryLogVO> logs(com.gas.ecommerce.modules.inventory.dto.InventoryLogQuery q);
    com.gas.ecommerce.modules.inventory.vo.InventoryLogVO create(Long operatorId,com.gas.ecommerce.modules.inventory.dto.InventoryCreateRequest r);
    com.gas.ecommerce.modules.inventory.vo.InventoryLogVO inbound(Long operatorId,Long id,com.gas.ecommerce.modules.inventory.dto.InventoryChangeRequest r);
    com.gas.ecommerce.modules.inventory.vo.InventoryLogVO outbound(Long operatorId,Long id,com.gas.ecommerce.modules.inventory.dto.InventoryChangeRequest r);
    com.gas.ecommerce.modules.inventory.vo.InventoryLogVO adjust(Long operatorId,Long id,com.gas.ecommerce.modules.inventory.dto.InventoryAdjustRequest r);
}
