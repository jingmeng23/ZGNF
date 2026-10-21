package com.gas.ecommerce.modules.system.service.impl;
// MyBatis-Plus 3.5.17 moved Spring Service APIs from extension.service to spring.service.
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.gas.ecommerce.modules.system.entity.Store;
import com.gas.ecommerce.modules.system.mapper.StoreMapper;
import com.gas.ecommerce.modules.system.service.StoreService;
@org.springframework.stereotype.Service public class StoreServiceImpl extends ServiceImpl<StoreMapper, Store> implements StoreService { }
