package com.gas.ecommerce.modules.user.service.impl;
// MyBatis-Plus 3.5.17 将 Spring Service 接口包从 extension.service 调整到了 spring.service。
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.gas.ecommerce.modules.user.entity.User;
import com.gas.ecommerce.modules.user.mapper.UserMapper;
import com.gas.ecommerce.modules.user.service.UserService;
/**
 * 业务服务实现类；当前主要继承 MyBatis-Plus 通用 CRUD，复杂业务流程仍需补充。
 */
@org.springframework.stereotype.Service public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService { }
