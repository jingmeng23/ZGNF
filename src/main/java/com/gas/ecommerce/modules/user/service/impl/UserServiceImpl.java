package com.gas.ecommerce.modules.user.service.impl;
// MyBatis-Plus 3.5.17 moved Spring Service APIs from extension.service to spring.service.
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.gas.ecommerce.modules.user.entity.User;
import com.gas.ecommerce.modules.user.mapper.UserMapper;
import com.gas.ecommerce.modules.user.service.UserService;
@org.springframework.stereotype.Service public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService { }
