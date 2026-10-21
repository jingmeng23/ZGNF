package com.gas.ecommerce.modules.inventory.service.impl;
// MyBatis-Plus 3.5.17 moved Spring Service APIs from extension.service to spring.service.
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.gas.ecommerce.modules.inventory.entity.Inventory;
import com.gas.ecommerce.modules.inventory.mapper.InventoryMapper;
import com.gas.ecommerce.modules.inventory.service.InventoryService;
@org.springframework.stereotype.Service public class InventoryServiceImpl extends ServiceImpl<InventoryMapper, Inventory> implements InventoryService { }
