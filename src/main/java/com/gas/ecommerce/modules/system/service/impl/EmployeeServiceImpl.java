package com.gas.ecommerce.modules.system.service.impl;
// MyBatis-Plus 3.5.17 moved Spring Service APIs from extension.service to spring.service.
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.gas.ecommerce.modules.system.entity.Employee;
import com.gas.ecommerce.modules.system.mapper.EmployeeMapper;
import com.gas.ecommerce.modules.system.service.EmployeeService;
@org.springframework.stereotype.Service public class EmployeeServiceImpl extends ServiceImpl<EmployeeMapper, Employee> implements EmployeeService { }
