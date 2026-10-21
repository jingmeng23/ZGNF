package com.gas.ecommerce.modules.user.service.impl;
// MyBatis-Plus 3.5.17 moved Spring Service APIs from extension.service to spring.service.
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.gas.ecommerce.modules.user.entity.UserAddress;
import com.gas.ecommerce.modules.user.mapper.UserAddressMapper;
import com.gas.ecommerce.modules.user.service.UserAddressService;
@org.springframework.stereotype.Service public class UserAddressServiceImpl extends ServiceImpl<UserAddressMapper, UserAddress> implements UserAddressService { }
