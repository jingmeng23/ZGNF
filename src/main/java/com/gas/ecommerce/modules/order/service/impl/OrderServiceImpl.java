package com.gas.ecommerce.modules.order.service.impl;
// MyBatis-Plus 3.5.17 moved Spring Service APIs from extension.service to spring.service.
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.gas.ecommerce.modules.order.entity.Order;
import com.gas.ecommerce.modules.order.mapper.OrderMapper;
import com.gas.ecommerce.modules.order.service.OrderService;
@org.springframework.stereotype.Service public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService { }
