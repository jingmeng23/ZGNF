package com.gas.ecommerce.common.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Dedicated address/cart/order owner for temporary no-login testing. */
@Service
public class TestIdentityService {
    public static final long USER_ID = -1L;
    private final JdbcTemplate db;
    public TestIdentityService(JdbcTemplate db) { this.db = db; }
    public void ensureUser() {
        db.update("INSERT INTO `user`(id,nickname,status) VALUES(?,'Temporary test user','active') ON DUPLICATE KEY UPDATE id=id", USER_ID);
    }
}
