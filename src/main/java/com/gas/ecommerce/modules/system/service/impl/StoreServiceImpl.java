package com.gas.ecommerce.modules.system.service.impl;
// MyBatis-Plus 3.5.17 将 Spring Service 接口包从 extension.service 调整到了 spring.service。
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.gas.ecommerce.modules.system.entity.Store;
import com.gas.ecommerce.modules.system.mapper.StoreMapper;
import com.gas.ecommerce.modules.system.service.StoreService;
/**
 * 业务服务实现类；当前主要继承 MyBatis-Plus 通用 CRUD，复杂业务流程仍需补充。
 */
@org.springframework.stereotype.Service public class StoreServiceImpl extends ServiceImpl<StoreMapper, Store> implements StoreService { }
