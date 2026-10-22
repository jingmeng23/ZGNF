package com.gas.ecommerce.modules.order.service;
// MyBatis-Plus 3.5.17 将 Spring Service 接口包从 extension.service 调整到了 spring.service。
import com.baomidou.mybatisplus.spring.service.IService;
import com.gas.ecommerce.modules.order.entity.Order;
/**
 * 业务服务接口，供 Controller 调用；继承 IService 后包含通用 CRUD 契约。
 */
public interface OrderService extends IService<Order> { }
