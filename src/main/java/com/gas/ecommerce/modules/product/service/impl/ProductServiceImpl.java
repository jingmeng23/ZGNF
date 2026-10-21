package com.gas.ecommerce.modules.product.service.impl;
// MyBatis-Plus 3.5.17 moved Spring Service APIs from extension.service to spring.service.
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.gas.ecommerce.modules.product.entity.Product;
import com.gas.ecommerce.modules.product.mapper.ProductMapper;
import com.gas.ecommerce.modules.product.service.ProductService;
@org.springframework.stereotype.Service public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService { }
