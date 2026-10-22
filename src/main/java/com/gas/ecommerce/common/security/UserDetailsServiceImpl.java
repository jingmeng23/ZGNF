package com.gas.ecommerce.common.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
/**
 * 登录阶段，把用户名转成 UserDetails，供密码校验使用。
 */
public class UserDetailsServiceImpl implements UserDetailsService {
    private final com.gas.ecommerce.modules.system.mapper.EmployeeMapper employees;
    public UserDetailsServiceImpl(com.gas.ecommerce.modules.system.mapper.EmployeeMapper employees) { this.employees = employees; }
    @Override public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var employee = employees.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers
                .<com.gas.ecommerce.modules.system.entity.Employee>lambdaQuery()
                .eq(com.gas.ecommerce.modules.system.entity.Employee::getUsername, username));
        if (employee == null) throw new UsernameNotFoundException("用户不存在");
        // 员工角色和权限需要通过关联表显式加载；不能因为用户名而默认授予管理员权限。
        return org.springframework.security.core.userdetails.User.withUsername(employee.getUsername())
                .password(employee.getPasswordHash()).authorities(java.util.List.of())
                .disabled(!"active".equals(employee.getStatus())).build();
    }
}
