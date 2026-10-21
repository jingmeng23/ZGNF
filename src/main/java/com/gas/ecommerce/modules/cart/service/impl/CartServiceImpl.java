package com.gas.ecommerce.modules.cart.service.impl;
// MyBatis-Plus 3.5.17 moved Spring Service APIs from extension.service to spring.service.
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.gas.ecommerce.modules.cart.entity.CartItem;
import com.gas.ecommerce.modules.cart.mapper.CartItemMapper;
import com.gas.ecommerce.modules.cart.service.CartService;
@org.springframework.stereotype.Service public class CartServiceImpl extends ServiceImpl<CartItemMapper, CartItem> implements CartService { }
