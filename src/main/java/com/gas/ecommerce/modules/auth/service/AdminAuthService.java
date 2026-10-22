package com.gas.ecommerce.modules.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.ecommerce.common.security.JwtTokenProvider;
import com.gas.ecommerce.modules.auth.dto.*;
import com.gas.ecommerce.modules.auth.mapper.AdminAccessMapper;
import com.gas.ecommerce.modules.system.entity.Employee;
import com.gas.ecommerce.modules.system.mapper.EmployeeMapper;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthService {
    private final EmployeeMapper employees;
    private final AdminAccessMapper access;
    private final PasswordEncoder passwords;
    private final JwtTokenProvider tokens;
    private final long expiresIn;
    private final String dummyHash;

    public AdminAuthService(EmployeeMapper employees, AdminAccessMapper access, PasswordEncoder passwords,
            JwtTokenProvider tokens, @Value("${app.jwt.access-expiration-ms:7200000}") long expiration) {
        this.employees = employees; this.access = access; this.passwords = passwords;
        this.tokens = tokens; this.expiresIn = expiration / 1000;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public AdminLoginResponse login(AdminLoginRequest request) {
        Employee employee = employees.selectOne(Wrappers.<Employee>lambdaQuery().eq(Employee::getUsername, request.username()));
        boolean matches = passwords.matches(request.password(), employee == null ? dummyHash : employee.getPasswordHash());
        if (!matches || employee == null || !"active".equals(employee.getStatus())) throw invalid();
        AdminProfile profile = profile(employee.getId());
        return new AdminLoginResponse(tokens.generateAccessToken(employee.getId(), "admin"), expiresIn, profile);
    }

    public AdminProfile profile(Long id) {
        Employee employee = employees.selectById(id);
        if (employee == null || !"active".equals(employee.getStatus())) throw invalid();
        List<String> roles = access.roles(id);
        // 任一有效员工角色可进入管理端，功能访问由数据库中的细粒度权限决定。
        if (roles.isEmpty()) throw invalid();
        return new AdminProfile(id, employee.getUsername(), roles, access.permissions(id));
    }

    public List<SimpleGrantedAuthority> authorities(Long id) {
        AdminProfile profile = profile(id);
        List<SimpleGrantedAuthority> result = new ArrayList<>();
        result.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        profile.permissions().forEach(p -> result.add(new SimpleGrantedAuthority(p)));
        return result;
    }

    private BadCredentialsException invalid() { return new BadCredentialsException("账号、密码或管理员授权无效"); }
}
