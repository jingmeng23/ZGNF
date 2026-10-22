package com.gas.ecommerce.modules.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.ecommerce.modules.auth.mapper.AdminAccessMapper;
import com.gas.ecommerce.modules.system.entity.*;
import com.gas.ecommerce.modules.system.mapper.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 仅在显式开启时创建首个管理员，不修改已有员工账号的密码或授权。 */
@Component
@ConditionalOnProperty(name = "app.admin-bootstrap.enabled", havingValue = "true")
public class AdminBootstrap implements ApplicationRunner {
    private final EmployeeMapper employees;
    private final RoleMapper roles;
    private final AdminAccessMapper access;
    private final PasswordEncoder passwords;
    private final String username;
    private final String password;
    public AdminBootstrap(EmployeeMapper employees, RoleMapper roles, AdminAccessMapper access, PasswordEncoder passwords,
            @Value("${app.admin-bootstrap.username}") String username,
            @Value("${app.admin-bootstrap.password}") String password) {
        this.employees = employees; this.roles = roles; this.access = access; this.passwords = passwords;
        this.username = username; this.password = password;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() || username.length() > 64 || password.length() < 12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new IllegalStateException("管理员用户名不能为空，密码须为至少 12 个字符且不超过 72 字节");
        if (employees.exists(Wrappers.<Employee>lambdaQuery().eq(Employee::getUsername, username))) return;
        Role role = roles.selectOne(Wrappers.<Role>lambdaQuery().eq(Role::getRoleKey, "admin").eq(Role::getStatus, "active"));
        if (role == null) throw new IllegalStateException("请先执行管理员角色数据库迁移");
        Employee employee = new Employee();
        employee.setUsername(username); employee.setPasswordHash(passwords.encode(password));
        employee.setStatus("active"); employee.setIsDeleted(0);
        employees.insert(employee); access.assign(employee.getId(), role.getId());
    }
}
