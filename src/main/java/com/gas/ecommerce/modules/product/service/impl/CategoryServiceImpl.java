package com.gas.ecommerce.modules.product.service.impl;
// MyBatis-Plus 3.5.17 moved Spring Service APIs from extension.service to spring.service.
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.gas.ecommerce.modules.product.entity.Category;
import com.gas.ecommerce.modules.product.mapper.CategoryMapper;
import com.gas.ecommerce.modules.product.service.CategoryService;
@org.springframework.stereotype.Service public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService { }
