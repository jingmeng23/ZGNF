package com.gas.ecommerce.modules.system.service.impl;
// MyBatis-Plus 3.5.17 moved Spring Service APIs from extension.service to spring.service.
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.gas.ecommerce.modules.system.entity.Role;
import com.gas.ecommerce.modules.system.mapper.RoleMapper;
import com.gas.ecommerce.modules.system.service.RoleService;
@org.springframework.stereotype.Service public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService { }
