package com.gas.ecommerce.common.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Dedicated address/cart/order owner for temporary no-login testing. */
@Service
public class TestIdentityService {
    public static final long USER_ID = -1L;
    public static final long EMPLOYEE_ID = -1L;
    private final JdbcTemplate db;
    public TestIdentityService(JdbcTemplate db) { this.db = db; }
    public void ensureUser() {
        db.update("INSERT INTO `user`(id,nickname,status) VALUES(?,'Temporary test user','active') ON DUPLICATE KEY UPDATE id=id", USER_ID);
        db.update("INSERT INTO employee(id,username,password_hash,status) VALUES(?,'temporary_test_operator','','active') ON DUPLICATE KEY UPDATE status='active',is_deleted=0", EMPLOYEE_ID);
    }
}
